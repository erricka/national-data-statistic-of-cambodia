package com.kh.niswebportal.app.nis21.migrator

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
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Types
import javax.sql.DataSource

data class Nis21A12Record(
    val provinceCode: String?,
    val provinceNameKhmer: String,
    val year: Int,
    val numHivPersons: Int?
)

private data class Nis21A12YearColumnMapping(
    val year: Int,
    val colIndex: Int
)

@Service
class Nis21A12MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_21a12"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 21A12 import." }

        val records = file.inputStream.use { parseCsv21A12(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv21A12(inputStream: InputStream): List<Nis21A12Record> {
        val records = mutableListOf<Nis21A12Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.isEmpty()) return emptyList()

            // 1. Locate header row containing 'លទ្ធផល' or 'សរុប'
            var headerRowIndex = -1
            for (i in rows.indices) {
                val cellText = rows[i].getOrNull(1)?.trim()
                if (cellText == "លទ្ធផល" || cellText == "សរុប") {
                    headerRowIndex = i
                    break
                }
            }

            if (headerRowIndex == -1) return emptyList()

            // 2. Scan primary header row (cols 2..13) for standard yearly columns
            val yearMappings = detectYearMappings(rows[headerRowIndex])
            if (yearMappings.isEmpty()) return emptyList()

            // 3. Process province rows starting after headers and aggregate rows
            val startDataRowIndex = headerRowIndex + 2
            for (rowIndex in startDataRowIndex until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 2) continue

                val provinceCode = row.getOrNull(0)?.trim()
                val provinceNameKhmer = row.getOrNull(1)?.trim() ?: ""

                if (provinceNameKhmer.isBlank() || provinceNameKhmer == "លទ្ធផល" || provinceNameKhmer == "សរុប") continue

                for (mapping in yearMappings) {
                    val rawValue = row.getOrNull(mapping.colIndex)
                    val numHivPersons = parseKhmerInt(rawValue)

                    // Store explicit integer value or default 0 so data displays in UI
                    records.add(
                        Nis21A12Record(
                            provinceCode = if (provinceCode.isNullOrBlank()) null else provinceCode,
                            provinceNameKhmer = provinceNameKhmer,
                            year = mapping.year,
                            numHivPersons = numHivPersons ?: 0
                        )
                    )
                }
            }
        }
        return records
    }

    private fun detectYearMappings(headerRow: CSVRecord): List<Nis21A12YearColumnMapping> {
        val mappings = mutableListOf<Nis21A12YearColumnMapping>()

        // Restrict scan to standard yearly columns (cols 2..13)
        val maxCol = minOf(14, headerRow.size())
        for (colIndex in 2 until maxCol) {
            val cellText = headerRow.getOrNull(colIndex)?.trim() ?: ""
            val year = parseKhmerInt(cellText)

            if (year != null && year in 2000..2099) {
                mappings.add(Nis21A12YearColumnMapping(year = year, colIndex = colIndex))
            }
        }
        return mappings
    }

    private fun insertBatch(connection: Connection, records: List<Nis21A12Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_21a12_hiv_persons 
            (province_code, province_name_khmer, year, num_hiv_persons, upload_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (province_name_khmer, year, upload_id) DO UPDATE SET
                province_code = EXCLUDED.province_code,
                num_hiv_persons = EXCLUDED.num_hiv_persons
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.provinceCode)
                stmt.setString(2, r.provinceNameKhmer)
                stmt.setInt(3, r.year)
                setNullableInt(stmt, 4, r.numHivPersons)
                stmt.setString(5, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun setNullableInt(stmt: PreparedStatement, index: Int, value: Int?) {
        if (value != null) stmt.setInt(index, value) else stmt.setNull(index, Types.INTEGER)
    }

    private fun parseKhmerInt(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val khmerToLatinDigits = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
        val normalized = text.map { khmerToLatinDigits[it] ?: it }.joinToString("")
        val digitsOnly = normalized.replace(Regex("[^0-9]"), "")
        return digitsOnly.toIntOrNull()
    }

    
}