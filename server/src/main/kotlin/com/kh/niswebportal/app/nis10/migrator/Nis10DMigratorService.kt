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

data class Nis10DNationalBudgetRevenueRecord(
    val chapter: String?,
    val account: String?,
    val subAccount: String?,
    val descriptionKh: String?,
    val financeLawAmount: String?,
    val actualExecution: String?,
    val ratio: String?,
    val otherNotes: String?,
    val year: String
)

@Service
class Nis10DMigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_10d"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 10D import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv10D(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv10D(inputStream: InputStream): List<Nis10DNationalBudgetRevenueRecord> {
        val records = mutableListOf<Nis10DNationalBudgetRevenueRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            for (i in 8 until rows.size) {
                val row = rows[i]
                val col0 = row.getOrNull(0)?.trim()
                val col1 = row.getOrNull(1)?.trim()
                val col2 = row.getOrNull(2)?.trim()
                val col3 = row.getOrNull(3)?.trim()

                if (!col0.isNullOrBlank() || !col1.isNullOrBlank() || !col2.isNullOrBlank() || !col3.isNullOrBlank()) {
                    records.add(
                        Nis10DNationalBudgetRevenueRecord(
                            chapter = col0,
                            account = col1,
                            subAccount = col2,
                            descriptionKh = col3,
                            financeLawAmount = row.getOrNull(4)?.toLatinDigits()?.trim(),
                            actualExecution = row.getOrNull(5)?.toLatinDigits()?.trim(),
                            ratio = row.getOrNull(6)?.toLatinDigits()?.trim(),
                            otherNotes = row.getOrNull(7)?.trim(),
                            year = "2025"
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis10DNationalBudgetRevenueRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_10d_annual_national_budget_revenue
            (chapter, account, sub_account, description_kh, finance_law_amount, actual_execution, ratio, other_notes, year, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (upload_id, year, chapter, account, sub_account) DO UPDATE SET
                description_kh = EXCLUDED.description_kh,
                finance_law_amount = EXCLUDED.finance_law_amount,
                actual_execution = EXCLUDED.actual_execution,
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
                stmt.setString(5, r.financeLawAmount)
                stmt.setString(6, r.actualExecution)
                stmt.setString(7, r.ratio)
                stmt.setString(8, r.otherNotes)
                stmt.setString(9, r.year)
                stmt.setString(10, uploadId)
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