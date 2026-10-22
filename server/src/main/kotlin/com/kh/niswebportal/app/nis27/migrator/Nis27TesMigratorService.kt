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

data class Nis27TesRecord(
    val indicatorCode: String,
    val indicatorKh: String,
    val unit: String?,
    val year: String,
    val metricValue: String?
)

@Service
class Nis27TesMigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_27_tes"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 27_tes import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv27Tes(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv27Tes(inputStream: InputStream): List<Nis27TesRecord> {
        val records = mutableListOf<Nis27TesRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 5) return emptyList()

            // Header row with years is at row index 5
            val rowYearHeader = rows[5]

            val yearColMap = mutableMapOf<Int, String>()
            for (c in 3 until rowYearHeader.size()) {
                val yearVal = rowYearHeader.getOrNull(c)?.toLatinDigits()?.replace(",", "")?.trim() ?: ""
                if (yearVal.matches(Regex("^\\d{4}$"))) {
                    yearColMap[c] = yearVal
                }
            }

            // Data rows start from row index 6
            for (i in 6 until rows.size) {
                val row = rows[i]
                val indCode = row.getOrNull(0)?.trim() ?: ""
                val indKh = row.getOrNull(1)?.trim() ?: ""
                val unitVal = row.getOrNull(2)?.trim()

                if (indCode.isBlank() || indCode == "nan" || indKh.contains("Source:")) {
                    continue
                }

                for ((c, year) in yearColMap) {
                    val rawVal = row.getOrNull(c)

                    records.add(
                        Nis27TesRecord(
                            indicatorCode = indCode,
                            indicatorKh = indKh,
                            unit = unitVal,
                            year = year,
                            metricValue = cleanValue(rawVal)
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis27TesRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_27_tes_establishment_stats
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