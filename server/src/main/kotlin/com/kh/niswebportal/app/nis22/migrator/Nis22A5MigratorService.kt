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

data class Nis22A5Record(
    val metricCode: String,
    val descriptionEnglish: String,
    val year: Int,
    val lengthKm: BigDecimal?
)

private data class Nis22A5YearColumnMapping(
    val year: Int,
    val colIndex: Int
)

@Service
class Nis22A5MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_22a5"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 22a5 import." }

        val records = file.inputStream.use { parseCsv22A5(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv22A5(inputStream: InputStream): List<Nis22A5Record> {
        val records = mutableListOf<Nis22A5Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.isEmpty()) return emptyList()

            // 1. Find year header row containing '22a5_year' or 'Year'
            var headerRowIndex = -1
            for (i in rows.indices) {
                val cell0 = rows[i].getOrNull(0)?.trim()
                val cell1 = rows[i].getOrNull(1)?.trim()
                if (cell0 == "22a5_year" || cell1.equals("Year", ignoreCase = true)) {
                    headerRowIndex = i
                    break
                }
            }

            if (headerRowIndex == -1) return emptyList()

            // 2. Map year column positions starting at index 2
            val yearMappings = detectYearMappings(rows[headerRowIndex])
            if (yearMappings.isEmpty()) return emptyList()

            // 3. Extract optical fiber statistics
            for (rowIndex in (headerRowIndex + 2) until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 2) continue

                val metricCode = row.getOrNull(0)?.trim() ?: ""
                val descriptionEnglish = row.getOrNull(1)?.trim() ?: ""

                if (metricCode.isBlank() || metricCode.startsWith("Source:", ignoreCase = true)) continue

                for (mapping in yearMappings) {
                    val rawValue = row.getOrNull(mapping.colIndex)
                    val lengthKm = parseDecimalValue(rawValue)

                    records.add(
                        Nis22A5Record(
                            metricCode = metricCode,
                            descriptionEnglish = descriptionEnglish,
                            year = mapping.year,
                            lengthKm = lengthKm
                        )
                    )
                }
            }
        }
        return records
    }

    private fun detectYearMappings(headerRow: CSVRecord): List<Nis22A5YearColumnMapping> {
        val mappings = mutableListOf<Nis22A5YearColumnMapping>()

        for (colIndex in 2 until headerRow.size()) {
            val cellText = headerRow.getOrNull(colIndex)?.trim() ?: ""
            val year = parseDecimalValue(cellText)?.toInt()

            if (year != null && year in 2000..2099) {
                mappings.add(Nis22A5YearColumnMapping(year = year, colIndex = colIndex))
            }
        }
        return mappings
    }

    private fun insertBatch(connection: Connection, records: List<Nis22A5Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_22a5_optical_fiber_network 
            (metric_code, description_english, year, length_km, upload_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (metric_code, year, upload_id) DO UPDATE SET
                description_english = EXCLUDED.description_english,
                length_km = EXCLUDED.length_km
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.metricCode)
                stmt.setString(2, r.descriptionEnglish)
                stmt.setInt(3, r.year)
                setNullableBigDecimal(stmt, 4, r.lengthKm)
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