package com.kh.niswebportal.app.nis31.migrator

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

data class Nis31A1CivilServantRecord(
    val year: Int,
    val categoryAMale: Int,
    val categoryAFemale: Int,
    val categoryBMale: Int,
    val categoryBFemale: Int,
    val categoryCMale: Int,
    val categoryCFemale: Int
)

@Service
class Nis31A1MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_31a1"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 31A1 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv31A1(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv31A1(inputStream: InputStream): List<Nis31A1CivilServantRecord> {
        val records = mutableListOf<Nis31A1CivilServantRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 14) return emptyList()

            // Iterate over data rows starting from row index 14
            for (rowIndex in 14 until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 7) continue

                val rawYearStr = row.getOrNull(0)?.trim() ?: ""
                val year = extractYear(rawYearStr) ?: continue

                records.add(
                    Nis31A1CivilServantRecord(
                        year = year,
                        categoryAMale = parseKhmerInt(row.getOrNull(1)),
                        categoryAFemale = parseKhmerInt(row.getOrNull(2)),
                        categoryBMale = parseKhmerInt(row.getOrNull(3)),
                        categoryBFemale = parseKhmerInt(row.getOrNull(4)),
                        categoryCMale = parseKhmerInt(row.getOrNull(5)),
                        categoryCFemale = parseKhmerInt(row.getOrNull(6))
                    )
                )
            }
        }
        return records
    }

    private fun extractYear(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val normalized = text.toLatinDigits()

        val match4Digits = Regex("""\d{4}""").find(normalized)
        if (match4Digits != null) {
            return match4Digits.value.toIntOrNull()
        }

        val digitsOnly = normalized.replace(Regex("[^0-9]"), "")
        return if (digitsOnly.length >= 4) {
            digitsOnly.takeLast(4).toIntOrNull()
        } else {
            digitsOnly.toIntOrNull()
        }
    }

    /**
     * Converts raw text/Khmer digits to Int.
     * Returns 0 for empty, blank, "-", or null fields.
     */
    private fun parseKhmerInt(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        val trimmed = text.trim()

        if (trimmed == "-" || trimmed.contains("-")) return 0

        val normalized = trimmed.toLatinDigits()
        val cleanNumber = normalized.replace(Regex("[^0-9]"), "")
        return cleanNumber.toIntOrNull() ?: 0
    }

    private fun insertBatch(connection: Connection, records: List<Nis31A1CivilServantRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_31a1_civil_servant
            (year, category_a_male, category_a_female, category_b_male, category_b_female, category_c_male, category_c_female, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, upload_id) DO UPDATE SET
                category_a_male = EXCLUDED.category_a_male,
                category_a_female = EXCLUDED.category_a_female,
                category_b_male = EXCLUDED.category_b_male,
                category_b_female = EXCLUDED.category_b_female,
                category_c_male = EXCLUDED.category_c_male,
                category_c_female = EXCLUDED.category_c_female
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setInt(1, r.year)
                stmt.setInt(2, r.categoryAMale)
                stmt.setInt(3, r.categoryAFemale)
                stmt.setInt(4, r.categoryBMale)
                stmt.setInt(5, r.categoryBFemale)
                stmt.setInt(6, r.categoryCMale)
                stmt.setInt(7, r.categoryCFemale)
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

    // --- Helper Extensions ---

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}