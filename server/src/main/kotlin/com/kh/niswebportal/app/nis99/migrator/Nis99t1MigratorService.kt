package com.kh.niswebportal.app.nis99.migrator

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

data class InvestmentApprovalRecord(
    val dataType: String,
    val yearPeriod: String,
    val allProjectsCount: String?,
    val allProjectCost: String?,
    val allWorkingCapital: String?,
    val allFixedAsset: String?,
    val activeProjectsCount: String?,
    val activeProjectCost: String?,
    val activeWorkingCapital: String?,
    val activeFixedAsset: String?,
    val formerProjectsCount: String?,
    val formerProjectCost: String?,
    val formerWorkingCapital: String?,
    val formerFixedAsset: String?,
    val nonActiveProjectsCount: String?,
    val nonActiveProjectCost: String?,
    val nonActiveWorkingCapital: String?,
    val nonActiveFixedAsset: String?
)

@Service
class Nis99t1MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_99t1"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 99 T1 import." }

        val records = mutableListOf<InvestmentApprovalRecord>()

        file.inputStream.use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val parser = CSVParser(reader, CSVFormat.DEFAULT)
                val rows = parser.records

                var currentDataType = "ANNUAL_DATA"

                for (i in 13 until rows.size) {
                    val row = rows[i]
                    val yearCol = row.getOrNull(1)?.trim() ?: ""

                    val lowerYearCol = yearCol.lowercase()
                    if (lowerYearCol.startsWith("source") || lowerYearCol.startsWith("note") ||
                        lowerYearCol.startsWith("1/") || lowerYearCol.startsWith("2/")) {
                        break
                    }

                    if (yearCol.isBlank() || lowerYearCol == "nan") {
                        val textCheck = row.getOrNull(3)?.trim() ?: ""
                        if (textCheck.contains("Annual absolute change", ignoreCase = true)) {
                            currentDataType = "ABSOLUTE_CHANGE"
                        }
                        continue
                    }

                    val record = InvestmentApprovalRecord(
                        dataType = currentDataType,
                        yearPeriod = yearCol,
                        allProjectsCount = cleanValue(row.getOrNull(3)),
                        allProjectCost = cleanValue(row.getOrNull(4)),
                        allWorkingCapital = cleanValue(row.getOrNull(5)),
                        allFixedAsset = cleanValue(row.getOrNull(6)),
                        activeProjectsCount = cleanValue(row.getOrNull(7)),
                        activeProjectCost = cleanValue(row.getOrNull(8)),
                        activeWorkingCapital = cleanValue(row.getOrNull(9)),
                        activeFixedAsset = cleanValue(row.getOrNull(10)),
                        formerProjectsCount = cleanValue(row.getOrNull(11)),
                        formerProjectCost = cleanValue(row.getOrNull(12)),
                        formerWorkingCapital = cleanValue(row.getOrNull(13)),
                        formerFixedAsset = cleanValue(row.getOrNull(14)),
                        nonActiveProjectsCount = cleanValue(row.getOrNull(15)),
                        nonActiveProjectCost = cleanValue(row.getOrNull(16)),
                        nonActiveWorkingCapital = cleanValue(row.getOrNull(17)),
                        nonActiveFixedAsset = cleanValue(row.getOrNull(18))
                    )

                    records.add(record)
                }

                if (records.isNotEmpty()) {
                    dataSource.connection.use { connection ->
                        insertBatch(connection, records, uploadId)
                    }
                }
            }
        }

        return records.size
    }

    private fun insertBatch(connection: Connection, records: List<InvestmentApprovalRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_99t1_investment_project_approvals
            (data_type, year_period, all_projects_count, all_project_cost, all_working_capital, all_fixed_asset,
             active_projects_count, active_project_cost, active_working_capital, active_fixed_asset,
             former_projects_count, former_project_cost, former_working_capital, former_fixed_asset,
             non_active_projects_count, non_active_project_cost, non_active_working_capital, non_active_fixed_asset, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.dataType)
                stmt.setString(2, r.yearPeriod)
                stmt.setString(3, r.allProjectsCount)
                stmt.setString(4, r.allProjectCost)
                stmt.setString(5, r.allWorkingCapital)
                stmt.setString(6, r.allFixedAsset)
                stmt.setString(7, r.activeProjectsCount)
                stmt.setString(8, r.activeProjectCost)
                stmt.setString(9, r.activeWorkingCapital)
                stmt.setString(10, r.activeFixedAsset)
                stmt.setString(11, r.formerProjectsCount)
                stmt.setString(12, r.formerProjectCost)
                stmt.setString(13, r.formerWorkingCapital)
                stmt.setString(14, r.formerFixedAsset)
                stmt.setString(15, r.nonActiveProjectsCount)
                stmt.setString(16, r.nonActiveProjectCost)
                stmt.setString(17, r.nonActiveWorkingCapital)
                stmt.setString(18, r.nonActiveFixedAsset)
                stmt.setString(19, uploadId)
                stmt.addBatch()

                count++
                if (count % BATCH_SIZE == 0) stmt.executeBatch()
            }
            if (count % BATCH_SIZE != 0) stmt.executeBatch()
        }
    }

    private fun cleanValue(value: String?): String? {
        val cleaned = value?.trim()?.replace(",", "") ?: return null
        if (cleaned.isBlank() || cleaned == "-" || cleaned == "nan" || cleaned == "-  " || cleaned == " - ") return null
        return cleaned
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}