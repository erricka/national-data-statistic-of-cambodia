package com.kh.niswebportal.app.nis22.migrator

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

data class Nis22A3Record(
    val metricCode: String,
    val descriptionEnglish: String,
    val year: Int,
    val metricValue: BigDecimal?
)

private data class Nis22A3YearColumnMapping(
    val year: Int,
    val colIndex: Int
)

@Service
class Nis22A3MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_22a3"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 22a3 import." }

        val records = file.inputStream.use { parseCsv22A3(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv22A3(inputStream: InputStream): List<Nis22A3Record> {
        val records = mutableListOf<Nis22A3Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.isEmpty()) return emptyList()

            // 1. Locate year header row containing 'ID' or 'description'
            var headerRowIndex = -1
            for (i in rows.indices) {
                val cellId = rows[i].getOrNull(0)?.trim()
                if (cellId.equals("ID", ignoreCase = true) || cellId.equals("កូដ", ignoreCase = true)) {
                    headerRowIndex = i
                    break
                }
            }

            if (headerRowIndex == -1) return emptyList()

            // 2. Extract year mappings from header row starting at column index 2
            val yearMappings = detectYearMappings(rows[headerRowIndex])
            if (yearMappings.isEmpty()) return emptyList()

            // 3. Process data rows
            for (rowIndex in (headerRowIndex + 1) until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 2) continue

                val metricCode = row.getOrNull(0)?.trim() ?: ""
                val descriptionEnglish = row.getOrNull(1)?.trim() ?: ""

                if (metricCode.isBlank() || metricCode.startsWith("Source:", ignoreCase = true) || metricCode.startsWith("Note:", ignoreCase = true)) continue

                for (mapping in yearMappings) {
                    val rawValue = row.getOrNull(mapping.colIndex)
                    val metricValue = parseDecimalValue(rawValue)

                    records.add(
                        Nis22A3Record(
                            metricCode = metricCode,
                            descriptionEnglish = descriptionEnglish,
                            year = mapping.year,
                            metricValue = metricValue
                        )
                    )
                }
            }
        }
        return records
    }

    private fun detectYearMappings(headerRow: CSVRecord): List<Nis22A3YearColumnMapping> {
        val mappings = mutableListOf<Nis22A3YearColumnMapping>()

        for (colIndex in 2 until headerRow.size()) {
            val cellText = headerRow.getOrNull(colIndex)?.trim() ?: ""
            val year = parseDecimalValue(cellText)?.toInt()

            if (year != null && year in 2000..2099) {
                mappings.add(Nis22A3YearColumnMapping(year = year, colIndex = colIndex))
            }
        }
        return mappings
    }

    private fun insertBatch(connection: Connection, records: List<Nis22A3Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_22a3_telephone_density 
            (metric_code, description_english, year, metric_value, upload_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (metric_code, year, upload_id) DO UPDATE SET
                description_english = EXCLUDED.description_english,
                metric_value = EXCLUDED.metric_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.metricCode)
                stmt.setString(2, r.descriptionEnglish)
                stmt.setInt(3, r.year)
                setNullableBigDecimal(stmt, 4, r.metricValue)
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
        return normalized.toBigDecimalOrNull()
    }

    
}