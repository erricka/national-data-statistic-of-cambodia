package com.kh.niswebportal.app.nis20.migrator

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

data class Nis20A4CommunityRecord(
    val provinceName: String,
    val year: Int,
    val communityCount: Int?
)

@Service
class Nis20A4MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_20a4"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 20a4 import." }

        val records = file.inputStream.use { parseCsv20A4(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv20A4(inputStream: InputStream): List<Nis20A4CommunityRecord> {
        val records = mutableListOf<Nis20A4CommunityRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 6) return emptyList()

            // 1. Scan Row 2 (Index 2) to dynamically map column indexes to Years
            val yearMappings = mutableListOf<Pair<Int, Int>>() // Pair<Year, ColumnIndex>
            val yearRow = rows[2]
            val yearRegex = Regex("ឆ្នាំ\\s*([០-៩0-9]{4})")

            // Yearly data starts at column index 3 and increments by 1
            for (c in 3 until yearRow.size()) {
                val yearText = yearRow.getOrNull(c)?.trim() ?: ""
                val match = yearRegex.find(yearText)
                if (match != null) {
                    val year = parseKhmerInt(match.groupValues[1])
                    if (year != null) yearMappings.add(Pair(year, c))
                }
            }

            // 2. Extract Data (Provinces start at Row 5 in CSV/Excel)
            for (i in 5 until rows.size) {
                val row = rows[i]
                val province = row.getOrNull(1)?.trim() ?: ""

                if (province.isBlank() || province == "Pro_name") continue

                // A. Extract Overall Totals (Stored as year = 0 in column index 2)
                records.add(
                    Nis20A4CommunityRecord(
                        provinceName = province,
                        year = 0,
                        communityCount = parseKhmerInt(row.getOrNull(2))
                    )
                )

                // B. Extract Chronological Year Breakdowns
                for ((year, colIndex) in yearMappings) {
                    records.add(
                        Nis20A4CommunityRecord(
                            provinceName = province,
                            year = year,
                            communityCount = parseKhmerInt(row.getOrNull(colIndex))
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis20A4CommunityRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_20a4_indigenous_communities 
            (province_name, year, community_count, upload_id)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (province_name, year, upload_id) DO UPDATE SET
                community_count = EXCLUDED.community_count
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.provinceName)
                stmt.setInt(2, r.year)
                setNullableInt(stmt, 3, r.communityCount)
                stmt.setString(4, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun setNullableInt(stmt: PreparedStatement, index: Int, value: Int?) {
        if (value != null) stmt.setInt(index, value) else stmt.setNull(index, Types.INTEGER)
    }

    private fun parseKhmerInt(text: String?): Int? {
        if (text.isNullOrBlank() || text.trim() == "-") return null
        val khmerToLatinDigits = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
        val normalized = text.map { khmerToLatinDigits[it] ?: it }.joinToString("")
        val cleanString = normalized.replace(Regex("[^0-9]"), "")
        return cleanString.toIntOrNull()
    }

    
}