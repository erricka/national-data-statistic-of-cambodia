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

data class Nis28B2MunicipalLandUsePlanRecord(
    val year: Int,
    val provinceCode: String,
    val provinceNameKh: String,
    val unit: String = "Plans / Square Kilometers",
    val inProcess: Double = 0.0,
    val completed: Double = 0.0,
    val approved: Double = 0.0,
    val totalAreaKm2: Double = 0.0
)

@Service
class Nis28B2MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_28b2"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 28B2 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv28B2(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv28B2(inputStream: InputStream): List<Nis28B2MunicipalLandUsePlanRecord> {
        val records = mutableListOf<Nis28B2MunicipalLandUsePlanRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            // Locate year header row index (Row 6 in 28B2)
            val yearHeaderRowIndex = rows.indexOfFirst { row ->
                row.size() > 2 && row.getOrNull(2)?.trim()?.matches(Regex("2\\d{3}")) == true
            }

            if (yearHeaderRowIndex == -1 || yearHeaderRowIndex >= rows.size) return emptyList()

            val metricRow = rows.getOrNull(yearHeaderRowIndex - 1)
            val yearRow = rows[yearHeaderRowIndex]

            // Column maps for each status category across columns 2..21
            val procColMap = mutableMapOf<Int, Int>() // កំពុងដំណើរការ (In Process)
            val finColMap = mutableMapOf<Int, Int>()  // បញ្ចប់ (Completed)
            val aprvColMap = mutableMapOf<Int, Int>() // អនុម័ត (Approved)
            val kmColMap = mutableMapOf<Int, Int>()   // ទំហំ (គម២) (Area in Km2)

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
                        currentMetric.contains("Proc", ignoreCase = true) || colIdx in 2..6 -> procColMap[colIdx] = year
                        currentMetric.contains("Fin", ignoreCase = true) || colIdx in 7..11 -> finColMap[colIdx] = year
                        currentMetric.contains("Aprv", ignoreCase = true) || colIdx in 12..16 -> aprvColMap[colIdx] = year
                        currentMetric.contains("Km", ignoreCase = true) || colIdx in 17..21 -> kmColMap[colIdx] = year
                    }
                }
            }

            // Extract all unique years present across sections
            val years = (procColMap.values + finColMap.values + aprvColMap.values + kmColMap.values).toSortedSet()

            // Parse data rows starting immediately below row 6 (Rows 7..31)
            for (rowIndex in (yearHeaderRowIndex + 1) until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 2) continue

                val code = row.getOrNull(0)?.trim() ?: ""
                val provinceName = row.getOrNull(1)?.trim() ?: ""

                if (code.isBlank() || provinceName.contains("សរុប")) continue

                // Build lookup maps for this province row: Year -> Value
                val procByYear = procColMap.mapValues { (colIdx, _) -> parseKhmerDouble(row.getOrNull(colIdx)) }
                val finByYear = finColMap.mapValues { (colIdx, _) -> parseKhmerDouble(row.getOrNull(colIdx)) }
                val aprvByYear = aprvColMap.mapValues { (colIdx, _) -> parseKhmerDouble(row.getOrNull(colIdx)) }
                val kmByYear = kmColMap.mapValues { (colIdx, _) -> parseKhmerDouble(row.getOrNull(colIdx)) }

                for (year in years) {
                    val procCol = procColMap.entries.firstOrNull { it.value == year }?.key
                    val finCol = finColMap.entries.firstOrNull { it.value == year }?.key
                    val aprvCol = aprvColMap.entries.firstOrNull { it.value == year }?.key
                    val kmCol = kmColMap.entries.firstOrNull { it.value == year }?.key

                    val inProcVal = procCol?.let { procByYear[it] } ?: 0.0
                    val finVal = finCol?.let { finByYear[it] } ?: 0.0
                    val aprvVal = aprvCol?.let { aprvByYear[it] } ?: 0.0
                    val kmVal = kmCol?.let { kmByYear[it] } ?: 0.0

                    records.add(
                        Nis28B2MunicipalLandUsePlanRecord(
                            year = year,
                            provinceCode = code,
                            provinceNameKh = provinceName,
                            unit = "Plans / Square Kilometers",
                            inProcess = inProcVal,
                            completed = finVal,
                            approved = aprvVal,
                            totalAreaKm2 = kmVal
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

    private fun insertBatch(connection: Connection, records: List<Nis28B2MunicipalLandUsePlanRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_28b2_municipal_land_use_plan
            (year, province_code, province_name_kh, unit, in_process, completed, approved, total_area_km2, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, province_code, upload_id) DO UPDATE SET
                province_name_kh = EXCLUDED.province_name_kh,
                unit = EXCLUDED.unit,
                in_process = EXCLUDED.in_process,
                completed = EXCLUDED.completed,
                approved = EXCLUDED.approved,
                total_area_km2 = EXCLUDED.total_area_km2
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setInt(1, r.year)
                stmt.setString(2, r.provinceCode)
                stmt.setString(3, r.provinceNameKh)
                stmt.setString(4, r.unit)
                stmt.setDouble(5, r.inProcess)
                stmt.setDouble(6, r.completed)
                stmt.setDouble(7, r.approved)
                stmt.setDouble(8, r.totalAreaKm2)
                stmt.setString(9, uploadId)
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