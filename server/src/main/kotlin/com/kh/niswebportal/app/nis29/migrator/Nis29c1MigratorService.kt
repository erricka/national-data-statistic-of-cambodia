package com.kh.niswebportal.app.nis29.migrator

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

data class Nis29c1Record(
    val indicatorCode: String,
    val indicatorKh: String,
    val unit: String?,
    val year: String,
    val metricValue: String?
)

@Service
class Nis29c1MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_29c1"

    private companion object {
        private const val BATCH_SIZE = 1000
        private val KHMER_TO_LATIN_MAP = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )

        // Mapping column indexes to (Year, Metric Suffix, Metric Label Khmer)
        private val METRIC_COLUMNS = listOf(
            Triple(3, "2023", "អប្បបរមា"),
            Triple(4, "2023", "មធ្យម"),
            Triple(5, "2023", "អតិបរមា"),
            Triple(6, "2024", "អប្បបរមា"),
            Triple(7, "2024", "មធ្យម"),
            Triple(8, "2024", "អតិបរមា"),
            Triple(9, "2025", "អប្បបរមា"),
            Triple(10, "2025", "មធ្យម"),
            Triple(11, "2025", "អតិបរមា")
        )

        private val METRIC_SUFFIX_MAP = mapOf(
            "អប្បបរមា" to "min",
            "មធ្យម" to "avg",
            "អតិបរមា" to "max"
        )
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 29 C1 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv29c1(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv29c1(inputStream: InputStream): List<Nis29c1Record> {
        val records = mutableListOf<Nis29c1Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 10) return emptyList()

            // Station data rows start at index 10 and end before unrelated bottom rows
            for (i in 10 until rows.size) {
                val row = rows[i]
                val seqStr = row.getOrNull(0)?.trim() ?: ""
                val stationName = row.getOrNull(1)?.trim() ?: ""
                val stationCode = row.getOrNull(2)?.trim() ?: ""

                // Stop processing if we hit invalid/leftover rows from other sheets
                if (!seqStr.matches(Regex("^\\d+$")) || !stationCode.startsWith("29c1_stt")) {
                    break
                }

                for ((colIdx, year, metricKh) in METRIC_COLUMNS) {
                    val rawVal = row.getOrNull(colIdx)
                    val metricSuffix = METRIC_SUFFIX_MAP[metricKh] ?: "val"
                    val fullIndicatorCode = "${stationCode}_${metricSuffix}"
                    val fullIndicatorKh = "កម្ពស់ទឹក $metricKh - $stationName"

                    records.add(
                        Nis29c1Record(
                            indicatorCode = fullIndicatorCode,
                            indicatorKh = fullIndicatorKh,
                            unit = "m",
                            year = year,
                            metricValue = cleanValue(rawVal)
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis29c1Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_29c1_water_level_stats
            (indicator_code, indicator_kh, unit, year, metric_value, upload_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (indicator_code, year, upload_id) DO UPDATE SET
                indicator_kh = EXCLUDED.indicator_kh,
                unit = EXCLUDED.unit,
                metric_value = EXCLUDED.metric_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.indicatorCode)
                stmt.setString(2, r.indicatorKh)
                stmt.setString(3, r.unit)
                stmt.setString(4, r.year)
                stmt.setString(5, r.metricValue)
                stmt.setString(6, uploadId)
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

    private fun cleanValue(value: String?): String? {
        val cleaned = value?.trim()?.replace(" ", "")?.replace(",", "") ?: return null
        if (cleaned.isBlank() || cleaned == "-" || cleaned == "nan") return null
        return cleaned.toLatinDigits()
    }

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}