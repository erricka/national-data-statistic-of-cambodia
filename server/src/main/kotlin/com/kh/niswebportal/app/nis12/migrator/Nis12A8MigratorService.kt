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

data class Nis12A8Record(
    val provinceCode: String,
    val provinceName: String,
    val metricCode: String,
    val metricDescription: String,
    val year: Int,
    val metricValue: BigDecimal?
)

private data class Nis12A8ColMapping(
    val colIndex: Int,
    val year: Int,
    val metricCode: String,
    val metricDescription: String
)

@Service
class Nis12A8MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_12a8"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 12a8 import." }

        val records = file.inputStream.use { parseCsv12A8(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv12A8(inputStream: InputStream): List<Nis12A8Record> {
        val records = mutableListOf<Nis12A8Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records
            if (rows.size < 9) return emptyList()

            // Header mapping rows for 12a8 CSV:
            // Row index 6 (Year), Row index 7 (Metric Description), Row index 8 (Metric Code)
            val yearRow = rows[6]
            val descRow = rows[7]
            val codeRow = rows[8]

            val columnMappings = mutableListOf<Nis12A8ColMapping>()
            var currentYear = 2010

            for (colIndex in 2 until yearRow.size()) {
                val yearCell = yearRow.getOrNull(colIndex)?.trim()
                if (!yearCell.isNullOrBlank()) {
                    val parsedYear = parseDecimalValue(yearCell)?.toInt()
                    if (parsedYear != null && parsedYear in 2000..2099) {
                        currentYear = parsedYear
                    }
                }

                val metricCode = codeRow.getOrNull(colIndex)?.trim() ?: ""
                val metricDesc = descRow.getOrNull(colIndex)?.trim() ?: ""

                if (metricCode.isNotBlank() && !metricCode.equals("Pro_name", ignoreCase = true)) {
                    columnMappings.add(
                        Nis12A8ColMapping(
                            colIndex = colIndex,
                            year = currentYear,
                            metricCode = metricCode,
                            metricDescription = metricDesc
                        )
                    )
                }
            }

            // Data rows start at row index 9
            for (rowIndex in 9 until rows.size) {
                val row = rows[rowIndex]
                val provinceCode = row.getOrNull(0)?.trim() ?: ""
                val provinceName = row.getOrNull(1)?.trim() ?: ""

                if (provinceCode.isBlank() || provinceCode.equals("Pro_Code", ignoreCase = true)) continue

                for (mapping in columnMappings) {
                    val rawValue = row.getOrNull(mapping.colIndex)
                    val value = parseDecimalValue(rawValue)

                    records.add(
                        Nis12A8Record(
                            provinceCode = provinceCode,
                            provinceName = provinceName,
                            metricCode = mapping.metricCode,
                            metricDescription = mapping.metricDescription,
                            year = mapping.year,
                            metricValue = value
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis12A8Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_12a8_facility_deliveries 
            (province_code, province_name, metric_code, metric_description, year, metric_value, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (province_code, metric_code, year, upload_id) DO UPDATE SET
                province_name = EXCLUDED.province_name,
                metric_description = EXCLUDED.metric_description,
                metric_value = EXCLUDED.metric_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.provinceCode)
                stmt.setString(2, r.provinceName)
                stmt.setString(3, r.metricCode)
                stmt.setString(4, r.metricDescription)
                stmt.setInt(5, r.year)
                setNullableBigDecimal(stmt, 6, r.metricValue)
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