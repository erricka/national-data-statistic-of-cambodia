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

data class Nis10EBudgetExpenditureRecord(
    val chapter: String?,
    val account: String?,
    val subAccount: String?,
    val descriptionKh: String?,
    val initialFinanceLaw: String?,
    val internalCreditMovement: String?,
    val unplannedCredit: String?,
    val supplementaryCredit: String?,
    val totalIncreaseCredit: String?,
    val decreaseCredit: String?,
    val newCreditStatus: String?,
    val actualExecution: String?,
    val remainingCredit: String?,
    val ratio: String?,
    val otherNotes: String?,
    val year: String
)

@Service
class Nis10EMigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_10e"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 10E import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv10E(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv10E(inputStream: InputStream): List<Nis10EBudgetExpenditureRecord> {
        val records = mutableListOf<Nis10EBudgetExpenditureRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            for (i in 10 until rows.size) {
                val row = rows[i]
                val col0 = row.getOrNull(0)?.trim()
                val col1 = row.getOrNull(1)?.trim()
                val col2 = row.getOrNull(2)?.trim()
                val col3 = row.getOrNull(3)?.trim()

                if (!col0.isNullOrBlank() || !col1.isNullOrBlank() || !col2.isNullOrBlank() || !col3.isNullOrBlank()) {
                    records.add(
                        Nis10EBudgetExpenditureRecord(
                            chapter = col0,
                            account = col1,
                            subAccount = col2,
                            descriptionKh = col3,
                            initialFinanceLaw = row.getOrNull(4)?.toLatinDigits()?.trim(),
                            internalCreditMovement = row.getOrNull(5)?.toLatinDigits()?.trim(),
                            unplannedCredit = row.getOrNull(6)?.toLatinDigits()?.trim(),
                            supplementaryCredit = row.getOrNull(7)?.toLatinDigits()?.trim(),
                            totalIncreaseCredit = row.getOrNull(8)?.toLatinDigits()?.trim(),
                            decreaseCredit = row.getOrNull(9)?.toLatinDigits()?.trim(),
                            newCreditStatus = row.getOrNull(10)?.toLatinDigits()?.trim(),
                            actualExecution = row.getOrNull(11)?.toLatinDigits()?.trim(),
                            remainingCredit = row.getOrNull(12)?.toLatinDigits()?.trim(),
                            ratio = row.getOrNull(13)?.toLatinDigits()?.trim(),
                            otherNotes = row.getOrNull(14)?.trim(),
                            year = "2025"
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis10EBudgetExpenditureRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_10e_annual_national_budget_expenditure
            (chapter, account, sub_account, description_kh, initial_finance_law, internal_credit_movement, 
             unplanned_credit, supplementary_credit, total_increase_credit, decrease_credit, 
             new_credit_status, actual_execution, remaining_credit, ratio, other_notes, year, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (upload_id, year, chapter, account, sub_account) DO UPDATE SET
                description_kh = EXCLUDED.description_kh,
                initial_finance_law = EXCLUDED.initial_finance_law,
                internal_credit_movement = EXCLUDED.internal_credit_movement,
                unplanned_credit = EXCLUDED.unplanned_credit,
                supplementary_credit = EXCLUDED.supplementary_credit,
                total_increase_credit = EXCLUDED.total_increase_credit,
                decrease_credit = EXCLUDED.decrease_credit,
                new_credit_status = EXCLUDED.new_credit_status,
                actual_execution = EXCLUDED.actual_execution,
                remaining_credit = EXCLUDED.remaining_credit,
                ratio = EXCLUDED.ratio,
                other_notes = EXCLUDED.other_notes
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.chapter)
                stmt.setString(2, r.account)
                stmt.setString(3, r.subAccount)
                stmt.setString(4, r.descriptionKh)
                stmt.setString(5, r.initialFinanceLaw)
                stmt.setString(6, r.internalCreditMovement)
                stmt.setString(7, r.unplannedCredit)
                stmt.setString(8, r.supplementaryCredit)
                stmt.setString(9, r.totalIncreaseCredit)
                stmt.setString(10, r.decreaseCredit)
                stmt.setString(11, r.newCreditStatus)
                stmt.setString(12, r.actualExecution)
                stmt.setString(13, r.remainingCredit)
                stmt.setString(14, r.ratio)
                stmt.setString(15, r.otherNotes)
                stmt.setString(16, r.year)
                stmt.setString(17, uploadId)
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