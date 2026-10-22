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

data class Nis21A5RehabRecord(
    val provinceCode: String?,
    val provinceNameKhmer: String,
    val year: Int,
    val numPersons: Int?
)

private data class Nis21A5YearColumnMapping(
    val year: Int,
    val colIndex: Int
)

@Service
class Nis21A5MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_21a5"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 21A5 import." }

        val records = file.inputStream.use { parseCsv21A5(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv21A5(inputStream: InputStream): List<Nis21A5RehabRecord> {
        val records = mutableListOf<Nis21A5RehabRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.isEmpty()) return emptyList()

            // Locate header row containing 'លទ្ធផល' or target header
            var headerRowIndex = -1
            for (i in rows.indices) {
                val cellText = rows[i].getOrNull(1)?.trim()
                if (cellText == "លទ្ធផល" || cellText?.contains("រាជធានី") == true) {
                    headerRowIndex = i
                    break
                }
            }

            if (headerRowIndex == -1) return emptyList()

            val yearHeaderRow = rows[headerRowIndex]
            val yearMappings = detectYearMappings(yearHeaderRow)

            if (yearMappings.isEmpty()) return emptyList()

            // Parse target rows starting after header row
            for (rowIndex in (headerRowIndex + 1) until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 2) continue

                val provinceCode = row.getOrNull(0)?.trim()
                val provinceNameKhmer = row.getOrNull(1)?.trim() ?: ""

                if (provinceNameKhmer.isBlank()) continue

                for (mapping in yearMappings) {
                    val numPersons = parseKhmerInt(row.getOrNull(mapping.colIndex))

                    if (numPersons != null) {
                        records.add(
                            Nis21A5RehabRecord(
                                provinceCode = if (provinceCode.isNullOrBlank()) null else provinceCode,
                                provinceNameKhmer = provinceNameKhmer,
                                year = mapping.year,
                                numPersons = numPersons
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun detectYearMappings(yearHeaderRow: CSVRecord): List<Nis21A5YearColumnMapping> {
        val mappings = mutableListOf<Nis21A5YearColumnMapping>()

        for (colIndex in 2 until yearHeaderRow.size()) {
            val cellText = yearHeaderRow.getOrNull(colIndex)?.trim() ?: ""
            val year = parseKhmerInt(cellText)

            if (year != null && year > 2000) {
                mappings.add(Nis21A5YearColumnMapping(year = year, colIndex = colIndex))
            }
        }
        return mappings
    }

    private fun insertBatch(connection: Connection, records: List<Nis21A5RehabRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_21a5_disabled_rehab_persons 
            (province_code, province_name_khmer, year, num_persons, upload_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (province_name_khmer, year, upload_id) DO UPDATE SET
                province_code = EXCLUDED.province_code,
                num_persons = EXCLUDED.num_persons
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.provinceCode)
                stmt.setString(2, r.provinceNameKhmer)
                stmt.setInt(3, r.year)
                setNullableInt(stmt, 4, r.numPersons)
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