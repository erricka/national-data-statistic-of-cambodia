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

data class Nis05A4RetailRecord(
    val itemCode: String?,
    val itemNameKhmer: String,
    val unit: String?,
    val year: Int,
    val month: Int,
    val retailPrice: Double?
)

@Service
class Nis05A4MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_05a4"

    private data class PeriodColumnMapping(
        val year: Int,
        val month: Int,
        val colIndex: Int
    )

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 05a4 import." }

        val records = file.inputStream.use { parseCsv05A4(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv05A4(inputStream: InputStream): List<Nis05A4RetailRecord> {
        val records = mutableListOf<Nis05A4RetailRecord>()

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
                if (row.size() < 3) continue

                val itemCode = row.getOrNull(0)?.trim()
                val itemNameKhmer = row.getOrNull(1)?.trim()
                val unit = row.getOrNull(2)?.trim()

                if (itemNameKhmer.isNullOrBlank()) continue

                for (mapping in periodMappings) {
                    val priceText = row.getOrNull(mapping.colIndex)
                    val retailPrice = KhmerStringUtils.parseKhmerDouble(priceText)

                    if (retailPrice != null) {
                        records.add(
                            Nis05A4RetailRecord(
                                itemCode = itemCode.takeIf { !it.isNullOrBlank() },
                                itemNameKhmer = itemNameKhmer,
                                unit = unit.takeIf { !it.isNullOrBlank() },
                                year = mapping.year,
                                month = mapping.month,
                                retailPrice = retailPrice
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

        for (colIndex in 3 until lastCol) {
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

    private fun insertBatch(connection: Connection, records: List<Nis05A4RetailRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_05a4_retail_price 
            (item_code, item_name_khmer, unit, year, month, retail_price, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (item_name_khmer, year, month, upload_id) DO UPDATE SET
                item_code = EXCLUDED.item_code,
                unit = EXCLUDED.unit,
                retail_price = EXCLUDED.retail_price
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.itemCode)
                stmt.setString(2, r.itemNameKhmer)
                stmt.setString(3, r.unit)
                stmt.setInt(4, r.year)
                stmt.setInt(5, r.month)
                stmt.setNullableDouble(6, r.retailPrice)
                stmt.setString(7, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }
}
