package com.kh.niswebportal.app.nis18.migrator

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

data class Nis18a13Record(
    val year: String,
    val organizationCount: String?,
    val organizationNames: String?
)

@Service
class Nis18a13MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_18a13"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 18 A13 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv18a13(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv18a13(inputStream: InputStream): List<Nis18a13Record> {
        val records = mutableListOf<Nis18a13Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 9) return emptyList()

            // Data rows start from index 10 onwards (where column 0 is year)
            for (i in 10 until rows.size) {
                val row = rows[i]
                val rawYear = row.getOrNull(0)?.trim() ?: ""

                if (rawYear.isBlank() || rawYear == "nan") {
                    continue
                }

                val year = cleanYear(rawYear)
                if (year.isBlank()) continue

                val countVal = row.getOrNull(1)
                val namesVal = row.getOrNull(2)

                records.add(
                    Nis18a13Record(
                        year = year,
                        organizationCount = cleanValue(countVal),
                        organizationNames = cleanText(namesVal)
                    )
                )
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis18a13Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_18a13_cultural_organizations_stats
            (upload_id, year, organization_count, organization_names)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (year, upload_id) DO UPDATE SET
                organization_count = EXCLUDED.organization_count,
                organization_names = EXCLUDED.organization_names
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, uploadId)
                stmt.setString(2, r.year)
                stmt.setString(3, r.organizationCount)
                stmt.setString(4, r.organizationNames)
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

    private fun cleanYear(value: String?): String {
        val cleaned = value?.trim()?.replace(".0", "") ?: ""
        return cleaned.toLatinDigits()
    }

    private fun cleanValue(value: String?): String? {
        val cleaned = value?.trim()?.replace(" ", "")?.replace(",", "")?.replace(".0", "") ?: return null
        if (cleaned.isBlank() || cleaned == "-" || cleaned == "nan") return null
        return cleaned.toLatinDigits()
    }

    private fun cleanText(value: String?): String? {
        val cleaned = value?.trim() ?: return null
        if (cleaned.isBlank() || cleaned == "-" || cleaned == "nan") return null
        return cleaned
    }

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}