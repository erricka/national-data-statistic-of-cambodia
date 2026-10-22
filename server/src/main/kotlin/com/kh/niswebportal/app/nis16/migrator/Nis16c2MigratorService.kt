package com.kh.niswebportal.app.nis16.migrator

import com.kh.niswebportal.core.strategy.NisMigratorStrategy
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.io.InputStreamReader
import javax.sql.DataSource

@Service
class Nis16c2MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_16c2"

    private companion object {
        private val KHMER_TO_LATIN_MAP = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for nis_16c2 import." }

        return file.inputStream.use { inputStream ->
            parseAndSaveCsv(inputStream, uploadId)
        }
    }

    private fun parseAndSaveCsv(inputStream: InputStream, uploadId: String): Int {
        InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 8) return 0

            dataSource.connection.use { connection ->
                var totalRecords = 0

                val sql = """
                    INSERT INTO nis_16c2_budget_revenue (
                        nis_code, budget_year, chapter, account, sub_account, description,
                        financial_law, actual_implementation, percentage, notes, upload_id
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (nis_code, budget_year, upload_id) DO UPDATE SET
                        chapter = EXCLUDED.chapter,
                        account = EXCLUDED.account,
                        sub_account = EXCLUDED.sub_account,
                        description = EXCLUDED.description,
                        financial_law = EXCLUDED.financial_law,
                        actual_implementation = EXCLUDED.actual_implementation,
                        percentage = EXCLUDED.percentage,
                        notes = EXCLUDED.notes
                """.trimIndent()

                connection.prepareStatement(sql).use { stmt ->
                    for (i in 8 until rows.size) {
                        val row = rows[i]
                        val nisCode = row.getOrNull(0)?.trim() ?: ""
                        if (nisCode.isBlank() || nisCode.lowercase() == "nan") continue

                        if (nisCode.contains("ប្រភព") || nisCode.contains("Source")) break

                        val budgetYear = row.getOrNull(1)?.trim()?.toLatinDigits() ?: "2024"

                        stmt.setString(1, nisCode)                    // nis_code (Col 0)
                        stmt.setString(2, budgetYear)                 // budget_year (Col 1 - Vertical Year)
                        stmt.setString(3, row.getOrNull(2)?.trim())     // chapter (Col 2)
                        stmt.setString(4, row.getOrNull(3)?.trim())     // account (Col 3)
                        stmt.setString(5, row.getOrNull(4)?.trim())     // sub_account (Col 4)
                        stmt.setString(6, row.getOrNull(5)?.trim() ?: "") // description (Col 5)
                        stmt.setString(7, cleanValue(row.getOrNull(6)))   // financial_law (Col 6)
                        stmt.setString(8, cleanValue(row.getOrNull(7)))   // actual_implementation (Col 7)
                        stmt.setString(9, cleanValue(row.getOrNull(8)))   // percentage (Col 8)
                        stmt.setString(10, cleanValue(row.getOrNull(9)))  // notes (Col 9)
                        stmt.setString(11, uploadId)                      // upload_id

                        stmt.addBatch()
                        totalRecords++
                    }
                    stmt.executeBatch()
                }
                return totalRecords
            }
        }
    }

    private fun cleanValue(value: String?): String? {
        val cleaned = value?.trim()?.replace(",", "") ?: return null
        if (cleaned.isBlank() || cleaned == "-" || cleaned.lowercase() == "n.a" || cleaned.lowercase() == "nan") return null
        return cleaned.toLatinDigits()
    }

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }

    private fun org.apache.commons.csv.CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}