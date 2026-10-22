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
class Nis16c1MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_16c1"

    private companion object {
        private val KHMER_TO_LATIN_MAP = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for nis_16c1 import." }

        return file.inputStream.use { inputStream ->
            parseAndSaveCsv(inputStream, uploadId)
        }
    }

    private fun parseAndSaveCsv(inputStream: InputStream, uploadId: String): Int {
        InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 13) return 0

            dataSource.connection.use { connection ->
                var totalRecords = 0

                val sql = """
                    INSERT INTO nis_16c1_budget_statistics (
                        nis_code, budget_year, chapter, account, sub_account, description,
                        financial_law, credit_inc_internal, credit_inc_unplan, credit_inc_add,
                        credit_inc_total, credit_dec, new_credit, actual_implementation,
                        credit_balance, percentage, pay_balance_expense, upload_id
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (nis_code, budget_year, upload_id) DO UPDATE SET
                        chapter = EXCLUDED.chapter,
                        account = EXCLUDED.account,
                        sub_account = EXCLUDED.sub_account,
                        description = EXCLUDED.description,
                        financial_law = EXCLUDED.financial_law,
                        credit_inc_internal = EXCLUDED.credit_inc_internal,
                        credit_inc_unplan = EXCLUDED.credit_inc_unplan,
                        credit_inc_add = EXCLUDED.credit_inc_add,
                        credit_inc_total = EXCLUDED.credit_inc_total,
                        credit_dec = EXCLUDED.credit_dec,
                        new_credit = EXCLUDED.new_credit,
                        actual_implementation = EXCLUDED.actual_implementation,
                        credit_balance = EXCLUDED.credit_balance,
                        percentage = EXCLUDED.percentage,
                        pay_balance_expense = EXCLUDED.pay_balance_expense
                """.trimIndent()

                connection.prepareStatement(sql).use { stmt ->
                    for (i in 13 until rows.size) {
                        val row = rows[i]
                        val nisCode = row.getOrNull(0)?.trim() ?: ""
                        if (nisCode.isBlank() || nisCode.lowercase() == "nan") continue

                        if (nisCode.contains("ប្រភព") || nisCode.contains("Source")) break

                        // Map columns accurately based on the spreadsheet structure
                        val budgetYear = row.getOrNull(1)?.trim()?.toLatinDigits() ?: "2024" // Col 1: Year

                        stmt.setString(1, nisCode)                    // nis_code (Col 0)
                        stmt.setString(2, budgetYear)                 // budget_year (Col 1 - Vertical Year)
                        stmt.setString(3, row.getOrNull(2)?.trim())     // chapter / ជំពូក (Col 2)
                        stmt.setString(4, row.getOrNull(3)?.trim())     // account / គណនី (Col 3)
                        stmt.setString(5, row.getOrNull(4)?.trim())     // sub_account / អនុគណនី (Col 4)
                        stmt.setString(6, row.getOrNull(5)?.trim() ?: "") // description / បរិយាយ (Col 5)

                        // Financial data columns (Col 6 to 15)
                        stmt.setString(7, cleanValue(row.getOrNull(6)))   // financial_law
                        stmt.setString(8, cleanValue(row.getOrNull(7)))   // credit_inc_internal
                        stmt.setString(9, cleanValue(row.getOrNull(8)))   // credit_inc_unplan
                        stmt.setString(10, cleanValue(row.getOrNull(9)))  // credit_inc_add
                        stmt.setString(11, cleanValue(row.getOrNull(10))) // credit_inc_total
                        stmt.setString(12, cleanValue(row.getOrNull(11))) // credit_dec
                        stmt.setString(13, cleanValue(row.getOrNull(12))) // new_credit
                        stmt.setString(14, cleanValue(row.getOrNull(13))) // actual_implementation
                        stmt.setString(15, cleanValue(row.getOrNull(14))) // credit_balance
                        stmt.setString(16, cleanValue(row.getOrNull(15))) // percentage
                        stmt.setString(17, null)                          // pay_balance_expense (if column 16 is absent)
                        stmt.setString(18, uploadId)                      // upload_id

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