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

data class Nis12A1Record(
    val metricCode: String,
    val metricDescription: String,
    val year: Int,
    val staffCount: BigDecimal?
)

@Service
class Nis12A1MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_12a1"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 12a1 import." }

        val records = file.inputStream.use { parseCsv12A1(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv12A1(inputStream: InputStream): List<Nis12A1Record> {
        val records = mutableListOf<Nis12A1Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records
            if (rows.size < 8) return emptyList()

            // Header info in rows 6 and 7
            val descriptionRow = rows[6]
            val codeRow = rows[7]

            // We need to map columns 1 to 14 (Index 1 to 14 in the row)
            for (colIndex in 1 until 15) {
                val metricCode = codeRow.getOrNull(colIndex)?.trim() ?: ""
                val metricDescription = descriptionRow.getOrNull(colIndex)?.trim() ?: ""

                if (metricCode.isBlank()) continue

                // Start parsing data from row 8 onwards
                for (rowIndex in 8 until rows.size) {
                    val row = rows[rowIndex]
                    val yearStr = row.getOrNull(0)?.trim()
                    val year = yearStr?.toIntOrNull() ?: continue

                    val rawValue = row.getOrNull(colIndex)
                    val staffCount = parseDecimalValue(rawValue)

                    records.add(Nis12A1Record(metricCode, metricDescription, year, staffCount))
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis12A1Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_12a1_public_health_staff 
            (metric_code, metric_description, year, staff_count, upload_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (metric_code, year, upload_id) DO UPDATE SET
                metric_description = EXCLUDED.metric_description,
                staff_count = EXCLUDED.staff_count
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.metricCode)
                stmt.setString(2, r.metricDescription)
                stmt.setInt(3, r.year)
                setNullableBigDecimal(stmt, 4, r.staffCount)
                stmt.setString(5, uploadId)
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