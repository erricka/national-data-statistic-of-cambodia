package com.kh.niswebportal.app.nis19.migrator

import com.kh.niswebportal.core.strategy.NisMigratorStrategy
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.sql.Connection
import javax.sql.DataSource

data class ExpenditureRecord(
    val chapterCode: String?,
    val subAccountCode: String?,
    val accountCode: String?,
    val description: String,
    val financialLaw2022: String?,
    val creditMovementIncrease: String?,
    val unplannedCredits: String?,
    val supplementary: String?,
    val totalIncrease: String?,
    val creditMovementDecrease: String?,
    val auditStatus: String?,
    val newCreditStatus: String?,
    val actualImplementation: String?,
    val remainingCredit: String?,
    val ratioPercentage: String?,
    val remarks: String?
)

@Service
class Nis19a5MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_19a5"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 19 A5 import." }

        val expenditureRecords = mutableListOf<ExpenditureRecord>()

        file.inputStream.use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val parser = CSVParser(reader, CSVFormat.DEFAULT)
                val rows = parser.records

                for (i in 11 until rows.size) {
                    val row = rows[i]
                    val col1 = row.getOrNull(1)?.trim() ?: ""
                    val col4Description = row.getOrNull(4)?.trim() ?: ""

                    if (col1.contains("តារាងសរុបចំណូលថវិការដ្ឋ", ignoreCase = true)) {
                        break
                    }

                    if (col4Description.isBlank() && col1.isBlank()) continue

                    val rawCode = row.getOrNull(1)
                    val cleanedCode = cleanCode(rawCode)

                    // If the code is actually a long descriptive string, shift it to description
                    val finalChapterCode = if (cleanedCode != null && cleanedCode.length > 50) null else cleanedCode
                    val finalDescription = col4Description.ifBlank {
                        if (cleanedCode != null && cleanedCode.length > 50) cleanedCode else col1
                    }

                    expenditureRecords.add(
                        ExpenditureRecord(
                            chapterCode = finalChapterCode,
                            subAccountCode = cleanCode(row.getOrNull(2)),
                            accountCode = cleanCode(row.getOrNull(3)),
                            description = finalDescription,
                            financialLaw2022 = cleanValue(row.getOrNull(5)),
                            creditMovementIncrease = cleanValue(row.getOrNull(6)),
                            unplannedCredits = cleanValue(row.getOrNull(7)),
                            supplementary = cleanValue(row.getOrNull(8)),
                            totalIncrease = cleanValue(row.getOrNull(9)),
                            creditMovementDecrease = cleanValue(row.getOrNull(10)),
                            auditStatus = cleanValue(row.getOrNull(11)),
                            newCreditStatus = cleanValue(row.getOrNull(12)),
                            actualImplementation = cleanValue(row.getOrNull(13)),
                            remainingCredit = cleanValue(row.getOrNull(14)),
                            ratioPercentage = cleanValue(row.getOrNull(15)),
                            remarks = row.getOrNull(16)?.trim()?.ifBlank { null }
                        )
                    )
                }

                if (expenditureRecords.isNotEmpty()) {
                    dataSource.connection.use { connection ->
                        insertExpendituresBatch(connection, expenditureRecords, uploadId)
                    }
                }
            }
        }

        return expenditureRecords.size
    }

    private fun insertExpendituresBatch(connection: Connection, records: List<ExpenditureRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_19a5_ministry_expenditures
            (chapter_code, sub_account_code, account_code, description, financial_law_2022, credit_movement_increase, 
             unplanned_credits, supplementary, total_increase, credit_movement_decrease, audit_status, new_credit_status, 
             actual_implementation, remaining_credit, ratio_percentage, remarks, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.chapterCode)
                stmt.setString(2, r.subAccountCode)
                stmt.setString(3, r.accountCode)
                stmt.setString(4, r.description)
                stmt.setString(5, r.financialLaw2022)
                stmt.setString(6, r.creditMovementIncrease)
                stmt.setString(7, r.unplannedCredits)
                stmt.setString(8, r.supplementary)
                stmt.setString(9, r.totalIncrease)
                stmt.setString(10, r.creditMovementDecrease)
                stmt.setString(11, r.auditStatus)
                stmt.setString(12, r.newCreditStatus)
                stmt.setString(13, r.actualImplementation)
                stmt.setString(14, r.remainingCredit)
                stmt.setString(15, r.ratioPercentage)
                stmt.setString(16, r.remarks)
                stmt.setString(17, uploadId)
                stmt.addBatch()

                count++
                if (count % BATCH_SIZE == 0) stmt.executeBatch()
            }
            if (count % BATCH_SIZE != 0) stmt.executeBatch()
        }
    }

    private fun cleanCode(value: String?): String? {
        val cleaned = value?.trim()?.replace(" ", "")?.replace(",", "") ?: return null
        if (cleaned.isBlank() || cleaned == "-") return null
        return cleaned.toLatinDigits()
    }

    private fun cleanValue(value: String?): String? {
        val cleaned = value?.trim()?.replace(" ", "")?.replace(",", "") ?: return null
        if (cleaned.isBlank() || cleaned == "-" || cleaned == "nan" || cleaned.contains("DIV/0")) return null
        return cleaned.toLatinDigits()
    }

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}