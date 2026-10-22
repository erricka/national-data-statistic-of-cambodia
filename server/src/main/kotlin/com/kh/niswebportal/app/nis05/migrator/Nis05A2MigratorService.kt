package com.kh.niswebportal.app.nis05.migrator

import com.kh.niswebportal.core.strategy.NisMigratorStrategy
import com.kh.niswebportal.core.util.KhmerStringUtils
import com.kh.niswebportal.core.util.getOrNull
import com.kh.niswebportal.core.util.setNullableDouble
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

data class Nis05A2FinanceRecord(
    val itemCode: String?,
    val itemNameKhmer: String,
    val year: Int,
    val month: Int,
    val financeValue: Double?
)

@Service
class Nis05A2MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_05a2"

    private data class PeriodColumnMapping(
        val year: Int,
        val month: Int,
        val colIndex: Int
    )

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 05a2 import." }

        val records = file.inputStream.use { parseCsv05A2(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv05A2(inputStream: InputStream): List<Nis05A2FinanceRecord> {
        val records = mutableListOf<Nis05A2FinanceRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 3) return emptyList()

            val yearRow = rows[0]
            val monthRow = rows[1]

            val periodMappings = detectPeriodMappings(yearRow, monthRow)
            if (periodMappings.isEmpty()) return emptyList()

            for (i in 2 until rows.size) {
                val row = rows[i]
                if (row.size() < 2) continue

                val itemCode = row.getOrNull(0)?.trim()
                val itemNameKhmer = row.getOrNull(1)?.trim()

                if (itemNameKhmer.isNullOrBlank()) continue

                for (mapping in periodMappings) {
                    val financeValueText = row.getOrNull(mapping.colIndex)
                    val financeValue = KhmerStringUtils.parseKhmerDouble(financeValueText)

                    if (financeValue != null) {
                        records.add(
                            Nis05A2FinanceRecord(
                                itemCode = itemCode.takeIf { !it.isNullOrBlank() },
                                itemNameKhmer = itemNameKhmer,
                                year = mapping.year,
                                month = mapping.month,
                                financeValue = financeValue
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun detectPeriodMappings(yearRow: CSVRecord, monthRow: CSVRecord): List<PeriodColumnMapping> {
        val mappings = mutableListOf<PeriodColumnMapping>()
        var currentYear: Int? = null
        var lastMonth: Int? = null

        val lastCol = monthRow.size()

        for (colIndex in 2 until lastCol) {
            val monthText = monthRow.getOrNull(colIndex)?.trim() ?: ""
            val month = KhmerStringUtils.mapKhmerMonth(monthText)

            if (month == 1 && lastMonth == 12 && currentYear != null) {
                currentYear++
            }

            val yearText = yearRow.getOrNull(colIndex)?.trim()
            if (currentYear == null && !yearText.isNullOrBlank()) {
                val extractedYear = KhmerStringUtils.parseKhmerDouble(yearText)?.toInt()
                if (extractedYear != null && extractedYear > 2000) {
                    currentYear = extractedYear
                }
            }

            if (currentYear != null && month != null) {
                mappings.add(PeriodColumnMapping(currentYear, month, colIndex))
            }

            if (month != null) {
                lastMonth = month
            }
        }
        return mappings
    }

    private fun insertBatch(connection: Connection, records: List<Nis05A2FinanceRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_05a2_finance_statistics 
            (item_code, item_name_khmer, year, month, finance_value, upload_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (item_name_khmer, year, month, upload_id) DO UPDATE SET
                item_code = EXCLUDED.item_code,
                finance_value = EXCLUDED.finance_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.itemCode)
                stmt.setString(2, r.itemNameKhmer)
                stmt.setInt(3, r.year)
                stmt.setInt(4, r.month)
                stmt.setNullableDouble(5, r.financeValue)
                stmt.setString(6, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }
}
