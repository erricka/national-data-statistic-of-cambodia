package com.kh.niswebportal.app.nis10.migrator

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

data class Nis10CChallengesRequestsRecord(
    val indicatorCode: String,
    val indicatorNameKh: String,
    val unit: String?,
    val year: String,
    val metricValue: String?
)

@Service
class Nis10CMigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_10c"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 10C import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv10C(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv10C(inputStream: InputStream): List<Nis10CChallengesRequestsRecord> {
        val records = mutableListOf<Nis10CChallengesRequestsRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            // Detect year from Row index 6 (Col 2), default to "2025"
            var detectedYear = "2025"
            if (rows.size > 6) {
                val rawYear = rows[6].getOrNull(2)?.toLatinDigits()?.trim()
                if (!rawYear.isNullOrBlank()) {
                    detectedYear = rawYear.replace(".0", "").trim()
                }
            }

            for (row in rows) {
                val code = row.getOrNull(0)?.trim() ?: ""

                if (code.startsWith("10C")) {
                    val indicatorNameKh = row.getOrNull(1)?.trim() ?: ""
                    val unit = row.getOrNull(2)?.trim()
                    val rawVal = row.getOrNull(2)?.trim()
                    val metricValue = if (rawVal.isNullOrBlank()) null else rawVal.toLatinDigits()

                    records.add(
                        Nis10CChallengesRequestsRecord(
                            indicatorCode = code,
                            indicatorNameKh = indicatorNameKh,
                            unit = unit,
                            year = detectedYear,
                            metricValue = metricValue
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis10CChallengesRequestsRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_10c_challenges_requests_stats
            (indicator_code, indicator_name_kh, unit, year, metric_value, upload_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (indicator_code, year, upload_id) DO UPDATE SET
                indicator_name_kh = EXCLUDED.indicator_name_kh,
                unit = EXCLUDED.unit,
                metric_value = EXCLUDED.metric_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.indicatorCode)
                stmt.setString(2, r.indicatorNameKh)
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

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}