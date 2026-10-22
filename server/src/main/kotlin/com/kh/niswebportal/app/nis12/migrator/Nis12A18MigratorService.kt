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

data class Nis12A18Record(
    val categoryCode: String,
    val categoryNameKh: String,
    val itemCode: String,
    val itemNameKh: String,
    val year: Int,
    val amountMillionKhr: BigDecimal?,
    val percentage: BigDecimal?,
    val others: String?
)

@Service
class Nis12A18MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_12a18"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 12a18 import." }

        val records = file.inputStream.use { parseCsv12A18(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv12A18(inputStream: InputStream): List<Nis12A18Record> {
        val records = mutableListOf<Nis12A18Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            var currentCategoryCode = ""
            var currentCategoryName = ""
            var currentYear = 2024

            for (row in rows) {
                val col0 = row.getOrNull(0)?.trim() ?: ""
                val col1 = row.getOrNull(1)?.trim() ?: ""

                // Check for new section header (col0 starts with 12a18_)
                if (col0.startsWith("12a18_")) {
                    currentCategoryCode = col0
                    currentCategoryName = col1
                    continue
                }

                // Check for year declaration row
                val parsedYear = parseDecimalValue(col1)?.toInt()
                if (parsedYear != null && parsedYear in 2000..2099) {
                    currentYear = parsedYear
                    continue
                }

                // Check for metric data row (col1 starts with 12a18_ and is not a header code)
                if (col1.startsWith("12a18_") && !col1.endsWith("_yr")) {
                    val itemNameKh = row.getOrNull(2)?.trim() ?: ""
                    val amountMillionKhr = parseDecimalValue(row.getOrNull(3))
                    val percentage = parseDecimalValue(row.getOrNull(4))
                    val others = row.getOrNull(5)?.trim()

                    records.add(
                        Nis12A18Record(
                            categoryCode = currentCategoryCode,
                            categoryNameKh = currentCategoryName,
                            itemCode = col1,
                            itemNameKh = itemNameKh,
                            year = currentYear,
                            amountMillionKhr = amountMillionKhr,
                            percentage = percentage,
                            others = if (others.isNullOrBlank()) null else others
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis12A18Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_12a18_health_expenditure_report 
            (category_code, category_name_kh, item_code, item_name_kh, year, amount_million_khr, percentage, others, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (item_code, year, upload_id) DO UPDATE SET
                category_code = EXCLUDED.category_code,
                category_name_kh = EXCLUDED.category_name_kh,
                item_name_kh = EXCLUDED.item_name_kh,
                amount_million_khr = EXCLUDED.amount_million_khr,
                percentage = EXCLUDED.percentage,
                others = EXCLUDED.others
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.categoryCode)
                stmt.setString(2, r.categoryNameKh)
                stmt.setString(3, r.itemCode)
                stmt.setString(4, r.itemNameKh)
                stmt.setInt(5, r.year)
                setNullableBigDecimal(stmt, 6, r.amountMillionKhr)
                setNullableBigDecimal(stmt, 7, r.percentage)
                stmt.setString(8, r.others)
                stmt.setString(9, uploadId)
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