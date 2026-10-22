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

data class ActiveCompanyRecord(
    val dataType: String,
    val yearPeriod: String,
    val activeProjectsCount: String?,
    val activeProjectCost: String?,
    val activeFixedAsset: String?,
    val activeEquity: String?,
    val accumProjectsCount: String?,
    val accumProjectCost: String?,
    val accumFixedAsset: String?,
    val accumEquity: String?
)

@Service
class Nis99t2MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_99t2"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 99 T2 import." }

        val records = mutableListOf<ActiveCompanyRecord>()

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

                    val record = ActiveCompanyRecord(
                        dataType = currentDataType,
                        yearPeriod = yearCol,
                        activeProjectsCount = cleanValue(row.getOrNull(3)),
                        activeProjectCost = cleanValue(row.getOrNull(4)),
                        activeFixedAsset = cleanValue(row.getOrNull(5)),
                        activeEquity = cleanValue(row.getOrNull(6)),
                        accumProjectsCount = cleanValue(row.getOrNull(7)),
                        accumProjectCost = cleanValue(row.getOrNull(8)),
                        accumFixedAsset = cleanValue(row.getOrNull(9)),
                        accumEquity = cleanValue(row.getOrNull(10))
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

    private fun insertBatch(connection: Connection, records: List<ActiveCompanyRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_99t2_active_companies
            (data_type, year_period, active_projects_count, active_project_cost, active_fixed_asset, active_equity,
             accum_projects_count, accum_project_cost, accum_fixed_asset, accum_equity, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.dataType)
                stmt.setString(2, r.yearPeriod)
                stmt.setString(3, r.activeProjectsCount)
                stmt.setString(4, r.activeProjectCost)
                stmt.setString(5, r.activeFixedAsset)
                stmt.setString(6, r.activeEquity)
                stmt.setString(7, r.accumProjectsCount)
                stmt.setString(8, r.accumProjectCost)
                stmt.setString(9, r.accumFixedAsset)
                stmt.setString(10, r.accumEquity)
                stmt.setString(11, uploadId)
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