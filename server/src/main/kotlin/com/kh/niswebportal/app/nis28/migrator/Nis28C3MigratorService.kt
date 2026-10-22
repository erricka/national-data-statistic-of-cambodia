package com.kh.niswebportal.app.nis28.migrator

import com.kh.niswebportal.core.strategy.NisMigratorStrategy
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.sql.Connection
import javax.sql.DataSource

data class Nis28C3IndustrialConstructionRecord(
    val year: Int,
    val provinceCode: String,
    val provinceNameKh: String,
    val unit: String = "Projects / 1000m2 / Million USD",
    val numProjects: Double = 0.0,
    val constructionArea1000m2: Double = 0.0,
    val investmentValueMillionUsd: Double = 0.0
)

@Service
class Nis28C3MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_28c3"

    private companion object {
        private const val BATCH_SIZE = 1000
        private val KHMER_TO_LATIN_MAP = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 28C3 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv28C3(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv28C3(inputStream: InputStream): List<Nis28C3IndustrialConstructionRecord> {
        val records = mutableListOf<Nis28C3IndustrialConstructionRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            // Locate year header row index (Row 6 in 28C3)
            val yearHeaderRowIndex = rows.indexOfFirst { row ->
                row.size() > 2 && row.getOrNull(2)?.trim()?.matches(Regex("2\\d{3}")) == true
            }

            if (yearHeaderRowIndex == -1 || yearHeaderRowIndex >= rows.size) return emptyList()

            val metricRow = rows.getOrNull(yearHeaderRowIndex - 1)
            val yearRow = rows[yearHeaderRowIndex]

            // Column maps for each metric section across columns 2..16
            val projColMap = mutableMapOf<Int, Int>()  // ចំនួនគម្រោង (Number of projects)
            val areaColMap = mutableMapOf<Int, Int>()  // ផ្ទៃក្រឡាកម្រាលសំណង់ (Construction floor area)
            val invesColMap = mutableMapOf<Int, Int>() // តម្លៃប៉ាន់ស្មានទុនវិនិយោគ (Investment value)

            var currentMetric = ""
            for (colIdx in 2 until yearRow.size()) {
                val metricVal = metricRow?.getOrNull(colIdx)?.trim() ?: ""
                if (metricVal.isNotBlank()) {
                    currentMetric = metricVal
                }

                val yearVal = yearRow.getOrNull(colIdx)
                    ?.toLatinDigits()
                    ?.replace(",", "")
                    ?.replace(" ", "") ?: ""

                val yearMatch = Regex("2\\d{3}").find(yearVal)
                if (yearMatch != null) {
                    val year = yearMatch.value.toInt()
                    when {
                        currentMetric.contains("Num_Proj", ignoreCase = true) || colIdx in 2..6 -> projColMap[colIdx] = year
                        currentMetric.contains("Cons_Area", ignoreCase = true) || colIdx in 7..11 -> areaColMap[colIdx] = year
                        currentMetric.contains("Inves_Value", ignoreCase = true) || colIdx in 12..16 -> invesColMap[colIdx] = year
                    }
                }
            }

            // Extract all unique years present across sections
            val years = (projColMap.values + areaColMap.values + invesColMap.values).toSortedSet()

            // Parse data rows starting immediately below row 6 (Rows 7..31)
            for (rowIndex in (yearHeaderRowIndex + 1) until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 2) continue

                val code = row.getOrNull(0)?.trim() ?: ""
                val provinceName = row.getOrNull(1)?.trim() ?: ""

                if (code.isBlank() || provinceName.contains("សរុប")) continue

                // Build lookup maps for this province row: Column Index -> Double Value
                val projByYear = projColMap.mapValues { (colIdx, _) -> parseKhmerDouble(row.getOrNull(colIdx)) }
                val areaByYear = areaColMap.mapValues { (colIdx, _) -> parseKhmerDouble(row.getOrNull(colIdx)) }
                val invesByYear = invesColMap.mapValues { (colIdx, _) -> parseKhmerDouble(row.getOrNull(colIdx)) }

                for (year in years) {
                    val projCol = projColMap.entries.firstOrNull { it.value == year }?.key
                    val areaCol = areaColMap.entries.firstOrNull { it.value == year }?.key
                    val invesCol = invesColMap.entries.firstOrNull { it.value == year }?.key

                    val projVal = projCol?.let { projByYear[it] } ?: 0.0
                    val areaVal = areaCol?.let { areaByYear[it] } ?: 0.0
                    val invesVal = invesCol?.let { invesByYear[it] } ?: 0.0

                    records.add(
                        Nis28C3IndustrialConstructionRecord(
                            year = year,
                            provinceCode = code,
                            provinceNameKh = provinceName,
                            unit = "Projects / 1000m2 / Million USD",
                            numProjects = projVal,
                            constructionArea1000m2 = areaVal,
                            investmentValueMillionUsd = invesVal
                        )
                    )
                }
            }
        }
        return records
    }

    private fun parseKhmerDouble(text: String?): Double {
        if (text.isNullOrBlank()) return 0.0
        val trimmed = text.trim()

        if (trimmed == "-" || trimmed.contains("-")) return 0.0

        val normalized = trimmed.toLatinDigits()
        val cleanNumber = normalized.replace(",", "").replace(" ", "")
        return cleanNumber.toDoubleOrNull() ?: 0.0
    }

    private fun insertBatch(connection: Connection, records: List<Nis28C3IndustrialConstructionRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_28c3_industrial_construction_data
            (year, province_code, province_name_kh, unit, num_projects, construction_area_1000m2, investment_value_million_usd, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, province_code, upload_id) DO UPDATE SET
                province_name_kh = EXCLUDED.province_name_kh,
                unit = EXCLUDED.unit,
                num_projects = EXCLUDED.num_projects,
                construction_area_1000m2 = EXCLUDED.construction_area_1000m2,
                investment_value_million_usd = EXCLUDED.investment_value_million_usd
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setInt(1, r.year)
                stmt.setString(2, r.provinceCode)
                stmt.setString(3, r.provinceNameKh)
                stmt.setString(4, r.unit)
                stmt.setDouble(5, r.numProjects)
                stmt.setDouble(6, r.constructionArea1000m2)
                stmt.setDouble(7, r.investmentValueMillionUsd)
                stmt.setString(8, uploadId)
                stmt.addBatch()

                count++
                if (count % BATCH_SIZE == 0) {
                    stmt.executeBatch()
                }
            }
            if (count % BATCH_SIZE != 0) {
                stmt.executeBatch()
            }
        }
    }

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}