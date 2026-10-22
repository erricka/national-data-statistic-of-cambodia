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

data class NgoFundingRecord(
    val fundingSource: String,
    val fund2021: String?,
    val fund2022: String?,
    val fund2023: String?,
    val fund2024: String?,
    val fund2025: String?,
    val fund2026: String?
)

@Service
class Nis99t13MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_99t13"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 99 T13 import." }

        val records = mutableListOf<NgoFundingRecord>()

        file.inputStream.use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val parser = CSVParser(reader, CSVFormat.DEFAULT)
                val rows = parser.records

                // Rows 12 and 13 contain the funding sources ("NGOs Own Fund", "DPs Fund")
                for (i in 12 until rows.size) {
                    val row = rows[i]
                    val sourceCol = row.getOrNull(1)?.trim() ?: ""

                    if (sourceCol.isBlank() || sourceCol.lowercase() == "nan" || sourceCol.equals("Total", ignoreCase = true)) {
                        continue
                    }

                    val record = NgoFundingRecord(
                        fundingSource = sourceCol,
                        fund2021 = cleanValue(row.getOrNull(2)),
                        fund2022 = cleanValue(row.getOrNull(3)),
                        fund2023 = cleanValue(row.getOrNull(4)),
                        fund2024 = cleanValue(row.getOrNull(5)),
                        fund2025 = cleanValue(row.getOrNull(6)),
                        fund2026 = cleanValue(row.getOrNull(7))
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

    private fun insertBatch(connection: Connection, records: List<NgoFundingRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_99t13_ngo_funding
            (funding_source, fund_2021, fund_2022, fund_2023, fund_2024, fund_2025, fund_2026, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.fundingSource)
                stmt.setString(2, r.fund2021)
                stmt.setString(3, r.fund2022)
                stmt.setString(4, r.fund2023)
                stmt.setString(5, r.fund2024)
                stmt.setString(6, r.fund2025)
                stmt.setString(7, r.fund2026)
                stmt.setString(8, uploadId)
                stmt.addBatch()

                count++
                if (count % BATCH_SIZE == 0) stmt.executeBatch()
            }
            if (count % BATCH_SIZE != 0) stmt.executeBatch()
        }
    }

    private fun cleanValue(value: String?): String? {
        val cleaned = value?.trim()?.replace(",", "") ?: return null
        if (cleaned.isBlank() || cleaned == "-" || cleaned == "nan" || cleaned == " - ") return null
        return cleaned
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}