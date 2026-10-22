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

data class Nis12A15Record(
    val provinceCode: String,
    val provinceName: String,
    val metricCode: String,
    val metricDescription: String,
    val year: Int,
    val metricValue: BigDecimal?
)

private data class Nis12A15ColMapping(
    val colIndex: Int,
    val year: Int,
    val metricCode: String,
    val metricDescription: String
)

@Service
class Nis12A15MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_12a15"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 12a15 import." }

        val records = file.inputStream.use { parseCsv12A15(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv12A15(inputStream: InputStream): List<Nis12A15Record> {
        val records = mutableListOf<Nis12A15Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records
            if (rows.size < 8) return emptyList()

            // Dynamic header search for the row containing header years
            var yearRowIndex = -1
            for (r in 0 until minOf(10, rows.size)) {
                val val2 = parseDecimalValue(rows[r].getOrNull(2))?.toInt()
                if (val2 != null && val2 in 2000..2099) {
                    yearRowIndex = r
                    break
                }
            }

            if (yearRowIndex == -1) return emptyList()

            val yearRow = rows[yearRowIndex]
            val columnMappings = mutableListOf<Nis12A15ColMapping>()

            for (colIndex in 2 until yearRow.size()) {
                val yearCell = yearRow.getOrNull(colIndex)?.trim()
                if (!yearCell.isNullOrBlank()) {
                    val parsedYear = parseDecimalValue(yearCell)?.toInt()
                    if (parsedYear != null && parsedYear in 2000..2099) {
                        columnMappings.add(
                            Nis12A15ColMapping(
                                colIndex = colIndex,
                                year = parsedYear,
                                metricCode = "12a15_hiv_new_cases",
                                metricDescription = "ចំនួនករណីឆ្លងថ្មីនៃមេរោគអេដស៍កម្រិតជាតិ"
                            )
                        )
                    }
                }
            }

            // Data rows start immediately after header row
            for (rowIndex in (yearRowIndex + 1) until rows.size) {
                val row = rows[rowIndex]
                val provinceCode = row.getOrNull(0)?.trim() ?: ""
                val provinceName = row.getOrNull(1)?.trim() ?: ""

                if (provinceCode.isBlank() || provinceCode.equals("code", ignoreCase = true)) continue

                for (mapping in columnMappings) {
                    val rawValue = row.getOrNull(mapping.colIndex)
                    val value = parseDecimalValue(rawValue)

                    records.add(
                        Nis12A15Record(
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

    private fun insertBatch(connection: Connection, records: List<Nis12A15Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_12a15_hiv_new_cases 
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