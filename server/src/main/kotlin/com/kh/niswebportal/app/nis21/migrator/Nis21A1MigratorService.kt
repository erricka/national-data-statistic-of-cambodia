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

data class Nis21A1VictimRecord(
    val provinceCode: String?,
    val provinceNameKhmer: String,
    val year: Int,
    val numVictims: Int?
)

private data class YearColumnMapping(
    val year: Int,
    val colIndex: Int
)

@Service
class Nis21A1MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_21a1"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 21A1 import." }

        val records = file.inputStream.use { parseCsv21A1(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv21A1(inputStream: InputStream): List<Nis21A1VictimRecord> {
        val records = mutableListOf<Nis21A1VictimRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 12) return emptyList()

            // Header containing years (2014, 2015, ..., 2025) is at row index 10
            val yearHeaderRow = rows[10]
            val yearMappings = detectYearMappings(yearHeaderRow)

            if (yearMappings.isEmpty()) return emptyList()

            // Data rows start at index 11 (e.g. "សរុប", "21a1_001 - បន្ទាយមានជ័យ", ..., "ក្រសួង")
            for (rowIndex in 11 until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 2) continue

                val provinceCode = row.getOrNull(0)?.trim()
                val provinceNameKhmer = row.getOrNull(1)?.trim() ?: ""

                if (provinceNameKhmer.isBlank()) continue

                for (mapping in yearMappings) {
                    val numVictims = parseKhmerInt(row.getOrNull(mapping.colIndex))

                    if (numVictims != null) {
                        records.add(
                            Nis21A1VictimRecord(
                                provinceCode = if (provinceCode.isNullOrBlank()) null else provinceCode,
                                provinceNameKhmer = provinceNameKhmer,
                                year = mapping.year,
                                numVictims = numVictims
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun detectYearMappings(yearHeaderRow: CSVRecord): List<YearColumnMapping> {
        val mappings = mutableListOf<YearColumnMapping>()

        for (colIndex in 2 until yearHeaderRow.size()) {
            val cellText = yearHeaderRow.getOrNull(colIndex)?.trim() ?: ""
            val year = parseKhmerInt(cellText)

            if (year != null && year > 2000) {
                mappings.add(YearColumnMapping(year = year, colIndex = colIndex))
            }
        }
        return mappings
    }

    private fun insertBatch(connection: Connection, records: List<Nis21A1VictimRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_21a1_community_victims 
            (province_code, province_name_khmer, year, num_victims, upload_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (province_name_khmer, year, upload_id) DO UPDATE SET
                province_code = EXCLUDED.province_code,
                num_victims = EXCLUDED.num_victims
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.provinceCode)
                stmt.setString(2, r.provinceNameKhmer)
                stmt.setInt(3, r.year)
                setNullableInt(stmt, 4, r.numVictims)
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