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

data class InvestmentLaborRecord(
    val yearPeriod: String,
    val sector: String,
    val manager: String?,
    val engineer: String?,
    val technician: String?,
    val supervisor: String?,
    val staff: String?,
    val skilledLabor: String?,
    val unskilledLabor: String?,
    val totalLabor: String?
)

@Service
class Nis99t11MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_99t11"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 99 T11 import." }

        val records = mutableListOf<InvestmentLaborRecord>()

        file.inputStream.use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val parser = CSVParser(reader, CSVFormat.DEFAULT)
                val rows = parser.records

                var currentYear = "2024" // Default active block based on layout structure

                for (i in 11 until rows.size) {
                    val row = rows[i]
                    val yearCol = row.getOrNull(1)?.trim() ?: ""
                    val sectorCol = row.getOrNull(2)?.trim() ?: ""

                    if (yearCol.isNotBlank() && yearCol.lowercase() != "nan" && !yearCol.startsWith("Year")) {
                        currentYear = yearCol.replace(" ", "")
                    }

                    if (sectorCol.isBlank() || sectorCol.lowercase() == "nan" || sectorCol.equals("Total", ignoreCase = true)) {
                        continue
                    }

                    val record = InvestmentLaborRecord(
                        yearPeriod = currentYear,
                        sector = sectorCol,
                        manager = cleanValue(row.getOrNull(4)),
                        engineer = cleanValue(row.getOrNull(5)),
                        technician = cleanValue(row.getOrNull(6)),
                        supervisor = cleanValue(row.getOrNull(7)),
                        staff = cleanValue(row.getOrNull(8)),
                        skilledLabor = cleanValue(row.getOrNull(9)),
                        unskilledLabor = cleanValue(row.getOrNull(10)),
                        totalLabor = cleanValue(row.getOrNull(11))
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

    private fun insertBatch(connection: Connection, records: List<InvestmentLaborRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_99t11_investment_labor
            (year_period, sector, manager, engineer, technician, supervisor, staff, skilled_labor, unskilled_labor, total_labor, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.yearPeriod)
                stmt.setString(2, r.sector)
                stmt.setString(3, r.manager)
                stmt.setString(4, r.engineer)
                stmt.setString(5, r.technician)
                stmt.setString(6, r.supervisor)
                stmt.setString(7, r.staff)
                stmt.setString(8, r.skilledLabor)
                stmt.setString(9, r.unskilledLabor)
                stmt.setString(10, r.totalLabor)
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
        if (cleaned.isBlank() || cleaned == "-" || cleaned == "nan" || cleaned == " - ") return null
        return cleaned
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}