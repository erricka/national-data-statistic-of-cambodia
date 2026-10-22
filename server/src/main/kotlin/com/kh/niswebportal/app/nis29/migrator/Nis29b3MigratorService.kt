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

data class Nis29b3Record(
    val indicatorCode: String,
    val indicatorKh: String,
    val unit: String?,
    val year: String,
    val metricValue: String?
)

@Service
class Nis29b3MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_29b3"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 29 B3 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv29b3(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv29b3(inputStream: InputStream): List<Nis29b3Record> {
        val records = mutableListOf<Nis29b3Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 8) return emptyList()

            // Header containing column names like 29b3_tmin_21, 29b3_tmin_22, etc. at row index 8
            val rowHeader = rows[8]
            val yearColMap = mutableMapOf<Int, String>()

            for (c in 2 until rowHeader.size()) {
                val headerStr = rowHeader.getOrNull(c) ?: ""
                val yearSuffix = Regex("\\d{2}$").find(headerStr)?.value
                if (yearSuffix != null) {
                    val fullYear = if (yearSuffix.toInt() >= 90) "19$yearSuffix" else "20$yearSuffix"
                    yearColMap[c] = fullYear
                }
            }

            // Data rows start from row index 9
            for (i in 9 until rows.size) {
                val row = rows[i]
                val indCode = row.getOrNull(0)?.trim() ?: ""
                val provinceName = row.getOrNull(1)?.trim() ?: ""

                if (indCode.isBlank() || indCode == "nan" || provinceName.isBlank()) {
                    continue
                }

                val indicatorKh = "សីតុណ្ហភាពអប្បបរមាមធ្យម - $provinceName"

                for ((c, year) in yearColMap) {
                    val rawVal = row.getOrNull(c)

                    records.add(
                        Nis29b3Record(
                            indicatorCode = indCode,
                            indicatorKh = indicatorKh,
                            unit = "°C",
                            year = year,
                            metricValue = cleanValue(rawVal)
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis29b3Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_29b3_min_temp_stats
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