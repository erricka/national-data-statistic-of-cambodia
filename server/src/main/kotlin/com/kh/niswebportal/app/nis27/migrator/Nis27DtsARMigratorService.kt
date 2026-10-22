package com.kh.niswebportal.app.nis27.migrator

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

data class Nis27DtsARRecord(
    val indicatorCode: String,
    val indicatorKh: String,
    val year: String,
    val metricValue: String?
)

@Service
class Nis27DtsARMigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_27_dts_a_r"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 27_dts_a_r import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv27DtsAR(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv27DtsAR(inputStream: InputStream): List<Nis27DtsARRecord> {
        val records = mutableListOf<Nis27DtsARRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 3) return emptyList()

            // Header row with years is at row index 3
            val rowYearHeader = rows[3]

            val yearColMap = mutableMapOf<Int, String>()
            for (c in 2 until rowYearHeader.size()) {
                val yearVal = rowYearHeader.getOrNull(c)?.toLatinDigits()?.replace(",", "")?.trim() ?: ""
                if (yearVal.matches(Regex("^\\d{4}$"))) {
                    yearColMap[c] = yearVal
                }
            }

            // Data rows start from row index 4
            for (i in 4 until rows.size) {
                val row = rows[i]
                val indCode = row.getOrNull(0)?.trim() ?: ""
                val indKh = row.getOrNull(1)?.trim() ?: ""

                if (indCode.isBlank() || indCode == "nan" || indKh.contains("Source:")) {
                    continue
                }

                for ((c, year) in yearColMap) {
                    val rawVal = row.getOrNull(c)

                    records.add(
                        Nis27DtsARRecord(
                            indicatorCode = indCode,
                            indicatorKh = indKh,
                            year = year,
                            metricValue = cleanValue(rawVal)
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis27DtsARRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_27_dts_a_r_region_stats
            (indicator_code, indicator_kh, year, metric_value, upload_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (indicator_code, year, upload_id) DO UPDATE SET
                indicator_kh = EXCLUDED.indicator_kh,
                metric_value = EXCLUDED.metric_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.indicatorCode)
                stmt.setString(2, r.indicatorKh)
                stmt.setString(3, r.year)
                stmt.setString(4, r.metricValue)
                stmt.setString(5, uploadId)
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