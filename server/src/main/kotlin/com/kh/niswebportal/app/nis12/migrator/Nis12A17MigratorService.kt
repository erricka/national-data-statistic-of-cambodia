package com.kh.niswebportal.app.nis12.migrator

import com.kh.niswebportal.core.util.getOrNull
import com.kh.niswebportal.core.strategy.NisMigratorStrategy
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.io.InputStreamReader
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Types
import javax.sql.DataSource

data class Nis12A17Record(
    val itemCode: String,
    val itemNameKh: String,
    val year: Int,
    val amountMillionKhr: BigDecimal?,
    val percentage: BigDecimal?,
    val others: String?
)

@Service
class Nis12A17MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_12a17"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 12a17 import." }

        val records = file.inputStream.use { parseCsv12A17(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv12A17(inputStream: InputStream): List<Nis12A17Record> {
        val records = mutableListOf<Nis12A17Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            // Locate report year from row index 10 (or row containing a 4-digit year)
            var reportYear = 2024
            for (r in 0 until minOf(12, rows.size)) {
                val yrVal = parseDecimalValue(rows[r].getOrNull(0))?.toInt()
                if (yrVal != null && yrVal in 2000..2099) {
                    reportYear = yrVal
                    break
                }
            }

            // Data rows start after row index 10
            for (rowIndex in 11 until rows.size) {
                val row = rows[rowIndex]
                val itemCode = row.getOrNull(0)?.trim() ?: ""
                val itemNameKh = row.getOrNull(1)?.trim() ?: ""

                if (itemCode.isBlank() && itemNameKh.isBlank()) continue

                val amountMillionKhr = parseDecimalValue(row.getOrNull(2))
                val percentage = parseDecimalValue(row.getOrNull(3))
                val others = row.getOrNull(4)?.trim()

                records.add(
                    Nis12A17Record(
                        itemCode = itemCode,
                        itemNameKh = itemNameKh,
                        year = reportYear,
                        amountMillionKhr = amountMillionKhr,
                        percentage = percentage,
                        others = if (others.isNullOrBlank()) null else others
                    )
                )
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis12A17Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_12a17_health_revenue_report 
            (item_code, item_name_kh, year, amount_million_khr, percentage, others, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (item_code, year, upload_id) DO UPDATE SET
                item_name_kh = EXCLUDED.item_name_kh,
                amount_million_khr = EXCLUDED.amount_million_khr,
                percentage = EXCLUDED.percentage,
                others = EXCLUDED.others
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.itemCode)
                stmt.setString(2, r.itemNameKh)
                stmt.setInt(3, r.year)
                setNullableBigDecimal(stmt, 4, r.amountMillionKhr)
                setNullableBigDecimal(stmt, 5, r.percentage)
                stmt.setString(6, r.others)
                stmt.setString(7, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun setNullableBigDecimal(stmt: PreparedStatement, index: Int, value: BigDecimal?) {
        if (value != null) stmt.setBigDecimal(index, value) else stmt.setNull(index, Types.NUMERIC)
    }

    private fun parseDecimalValue(text: String?): BigDecimal? {
        if (text.isNullOrBlank() || text.trim() == "-") return null
        val khmerToLatinDigits = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
        val normalized = text.map { khmerToLatinDigits[it] ?: it }.joinToString("").replace(",", "").trim()
        val digitsOnly = normalized.replace(Regex("[^0-9.]"), "")
        return digitsOnly.toBigDecimalOrNull()
    }

    
}