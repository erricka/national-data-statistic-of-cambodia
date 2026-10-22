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

data class Nis21A2ChildRecord(
    val provinceCode: String?,
    val provinceNameKhmer: String,
    val year: Int,
    val numChildren: Int?
)

private data class Nis21A2YearColumnMapping(
    val year: Int,
    val colIndex: Int
)

@Service
class Nis21A2MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_21a2"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 21A2 import." }

        val records = file.inputStream.use { parseCsv21A2(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv21A2(inputStream: InputStream): List<Nis21A2ChildRecord> {
        val records = mutableListOf<Nis21A2ChildRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 12) return emptyList()

            // Header containing years (2014, 2015, ..., 2025) is at row index 10
            val yearHeaderRow = rows[10]
            val yearMappings = detectYearMappings(yearHeaderRow)

            if (yearMappings.isEmpty()) return emptyList()

            // Data rows start at index 11 (row 11 is "សរុប", 12+ are provinces)
            for (rowIndex in 11 until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 2) continue

                val provinceCode = row.getOrNull(0)?.trim()
                val provinceNameKhmer = row.getOrNull(1)?.trim() ?: ""

                if (provinceNameKhmer.isBlank()) continue

                for (mapping in yearMappings) {
                    val numChildren = parseKhmerInt(row.getOrNull(mapping.colIndex))

                    if (numChildren != null) {
                        records.add(
                            Nis21A2ChildRecord(
                                provinceCode = if (provinceCode.isNullOrBlank()) null else provinceCode,
                                provinceNameKhmer = provinceNameKhmer,
                                year = mapping.year,
                                numChildren = numChildren
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun detectYearMappings(yearHeaderRow: CSVRecord): List<Nis21A2YearColumnMapping> {
        val mappings = mutableListOf<Nis21A2YearColumnMapping>()

        for (colIndex in 2 until yearHeaderRow.size()) {
            val cellText = yearHeaderRow.getOrNull(colIndex)?.trim() ?: ""
            val year = parseKhmerInt(cellText)

            if (year != null && year > 2000) {
                mappings.add(Nis21A2YearColumnMapping(year = year, colIndex = colIndex))
            }
        }
        return mappings
    }

    private fun insertBatch(connection: Connection, records: List<Nis21A2ChildRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_21a2_alternative_care_children 
            (province_code, province_name_khmer, year, num_children, upload_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (province_name_khmer, year, upload_id) DO UPDATE SET
                province_code = EXCLUDED.province_code,
                num_children = EXCLUDED.num_children
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.provinceCode)
                stmt.setString(2, r.provinceNameKhmer)
                stmt.setInt(3, r.year)
                setNullableInt(stmt, 4, r.numChildren)
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