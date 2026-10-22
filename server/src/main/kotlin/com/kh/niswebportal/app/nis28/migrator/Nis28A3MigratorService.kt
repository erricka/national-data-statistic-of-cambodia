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

data class Nis28A3SporadicLandRecord(
    val year: Int,
    val provinceCode: String,
    val provinceNameKh: String,
    val unit: String = "Titles / Households / Hectares",
    val totalTitles: Double = 0.0,
    val totalHouseholds: Double = 0.0,
    val totalHectares: Double = 0.0
)

@Service
class Nis28A3MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_28a3"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 28A3 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv28A3(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv28A3(inputStream: InputStream): List<Nis28A3SporadicLandRecord> {
        val records = mutableListOf<Nis28A3SporadicLandRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            val yearHeaderRowIndex = rows.indexOfFirst { row ->
                row.size() > 2 && row.getOrNull(2)?.trim()?.matches(Regex("2\\d{3}")) == true
            }

            if (yearHeaderRowIndex == -1 || yearHeaderRowIndex >= rows.size) return emptyList()

            val metricRow = rows.getOrNull(yearHeaderRowIndex - 1)
            val yearRow = rows[yearHeaderRowIndex]

            val pcsColMap = mutableMapOf<Int, Int>()
            val hhsColMap = mutableMapOf<Int, Int>()
            val hacColMap = mutableMapOf<Int, Int>()

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
                        currentMetric.contains("Pcs", ignoreCase = true) || colIdx in 2..6 -> pcsColMap[colIdx] = year
                        currentMetric.contains("HHs", ignoreCase = true) || colIdx in 7..11 -> hhsColMap[colIdx] = year
                        currentMetric.contains("Hac", ignoreCase = true) || colIdx in 12..16 -> hacColMap[colIdx] = year
                    }
                }
            }

            val years = (pcsColMap.values + hhsColMap.values + hacColMap.values).toSortedSet()

            for (rowIndex in (yearHeaderRowIndex + 1) until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 2) continue

                val code = row.getOrNull(0)?.trim() ?: ""
                val provinceName = row.getOrNull(1)?.trim() ?: ""

                if (code.isBlank() || provinceName.contains("សរុប")) continue

                val titlesByYear = pcsColMap.mapValues { (colIdx, _) -> parseKhmerDouble(row.getOrNull(colIdx)) }
                val householdsByYear = hhsColMap.mapValues { (colIdx, _) -> parseKhmerDouble(row.getOrNull(colIdx)) }
                val hectaresByYear = hacColMap.mapValues { (colIdx, _) -> parseKhmerDouble(row.getOrNull(colIdx)) }

                for (year in years) {
                    val pcsCol = pcsColMap.entries.firstOrNull { it.value == year }?.key
                    val hhsCol = hhsColMap.entries.firstOrNull { it.value == year }?.key
                    val hacCol = hacColMap.entries.firstOrNull { it.value == year }?.key

                    val titles = pcsCol?.let { titlesByYear[it] } ?: 0.0
                    val households = hhsCol?.let { householdsByYear[it] } ?: 0.0
                    val hectares = hacCol?.let { hectaresByYear[it] } ?: 0.0

                    records.add(
                        Nis28A3SporadicLandRecord(
                            year = year,
                            provinceCode = code,
                            provinceNameKh = provinceName,
                            unit = "Titles / Households / Hectares",
                            totalTitles = titles,
                            totalHouseholds = households,
                            totalHectares = hectares
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

    private fun insertBatch(connection: Connection, records: List<Nis28A3SporadicLandRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_28a3_sporadic_land_registration
            (year, province_code, province_name_kh, unit, total_titles, total_households, total_hectares, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, province_code, upload_id) DO UPDATE SET
                province_name_kh = EXCLUDED.province_name_kh,
                unit = EXCLUDED.unit,
                total_titles = EXCLUDED.total_titles,
                total_households = EXCLUDED.total_households,
                total_hectares = EXCLUDED.total_hectares
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setInt(1, r.year)
                stmt.setString(2, r.provinceCode)
                stmt.setString(3, r.provinceNameKh)
                stmt.setString(4, r.unit)
                stmt.setDouble(5, r.totalTitles)
                stmt.setDouble(6, r.totalHouseholds)
                stmt.setDouble(7, r.totalHectares)
                stmt.setString(8, uploadId)
                stmt.addBatch()

                count++
                if (count % BATCH_SIZE == 0) stmt.executeBatch()
            }
            if (count % BATCH_SIZE != 0) stmt.executeBatch()
        }
    }

    private fun String.toLatinDigits(): String = this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    private fun CSVRecord.getOrNull(index: Int): String? = if (index in 0 until this.size()) this.get(index) else null
}