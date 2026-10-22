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

data class FngoDisbursementRecord(
    val hqCountry: String,
    val ownFund2022: String?,
    val ownFund2023: String?,
    val ownFund2024: String?,
    val ownFund2025: String?
)

@Service
class Nis99t12MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_99t12"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 99 T12 import." }

        val records = mutableListOf<FngoDisbursementRecord>()

        file.inputStream.use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val parser = CSVParser(reader, CSVFormat.DEFAULT)
                val rows = parser.records

                for (i in 10 until rows.size) {
                    val row = rows[i]
                    val countryCol = row.getOrNull(1)?.trim() ?: ""

                    if (countryCol.isBlank() || countryCol.lowercase() == "nan" || countryCol.equals("Total", ignoreCase = true)) {
                        continue
                    }

                    val record = FngoDisbursementRecord(
                        hqCountry = countryCol,
                        ownFund2022 = cleanValue(row.getOrNull(2)),
                        ownFund2023 = cleanValue(row.getOrNull(3)),
                        ownFund2024 = cleanValue(row.getOrNull(4)),
                        ownFund2025 = cleanValue(row.getOrNull(5))
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

    private fun insertBatch(connection: Connection, records: List<FngoDisbursementRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_99t12_fngo_disbursement
            (hq_country, own_fund_2022, own_fund_2023, own_fund_2024, own_fund_2025, upload_id)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.hqCountry)
                stmt.setString(2, r.ownFund2022)
                stmt.setString(3, r.ownFund2023)
                stmt.setString(4, r.ownFund2024)
                stmt.setString(5, r.ownFund2025)
                stmt.setString(6, uploadId)
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