package com.kh.niswebportal.app.nis23.migrator

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

data class Nis23CChallengeRequestRecord(
    val indicatorCode: String,
    val descriptionKh: String,
    val category: String, // "CHALLENGE" or "REQUEST"
    val year: String
)

@Service
class Nis23CMigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_23c"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 23C import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv23C(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv23C(inputStream: InputStream): List<Nis23CChallengeRequestRecord> {
        val records = mutableListOf<Nis23CChallengeRequestRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            // Detect year from Row index 10 (Col 2), default to "2025"
            var detectedYear = "2025"
            if (rows.size > 10) {
                val rawYear = rows[10].getOrNull(2)?.toLatinDigits()?.trim()
                if (!rawYear.isNullOrBlank()) {
                    detectedYear = rawYear.replace(".0", "").trim()
                }
            }

            for (row in rows) {
                val rawCode = row.getOrNull(0)?.trim() ?: ""
                val normalizedCode = rawCode.toLatinDigits()

                if (normalizedCode.startsWith("23C1_") || normalizedCode.startsWith("23C2_")) {
                    val descriptionKh = row.getOrNull(1)?.trim() ?: ""
                    val category = if (normalizedCode.startsWith("23C1_")) "CHALLENGE" else "REQUEST"

                    records.add(
                        Nis23CChallengeRequestRecord(
                            indicatorCode = normalizedCode,
                            descriptionKh = descriptionKh,
                            category = category,
                            year = detectedYear
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis23CChallengeRequestRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_23c_challenges_requests
            (indicator_code, description_kh, category, year, upload_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (indicator_code, year, upload_id) DO UPDATE SET
                description_kh = EXCLUDED.description_kh,
                category = EXCLUDED.category
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.indicatorCode)
                stmt.setString(2, r.descriptionKh)
                stmt.setString(3, r.category)
                stmt.setString(4, r.year)
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

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}