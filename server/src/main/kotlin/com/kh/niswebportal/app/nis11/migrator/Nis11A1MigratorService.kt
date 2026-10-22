package com.kh.niswebportal.app.nis11.migrator

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
import javax.sql.DataSource

data class Nis11A1MediaRecord(
    val year: Int,
    val category: String,
    val serialNo: String,
    val mediaType: String,
    val ppCount: String,
    val relayCount: String,
    val totalCount: String,
    val remarks: String
)

@Service
class Nis11A1MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_11a1"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 11a1 import." }

        val records = file.inputStream.use { parseCsv11A1(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv11A1(inputStream: InputStream): List<Nis11A1MediaRecord> {
        val records = mutableListOf<Nis11A1MediaRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.isEmpty()) return emptyList()

            // The very first block in the Excel file lacks a Year header, defaulting to 2025
            var currentYear = 2025
            var currentCategory = ""

            // Regex to match "ឆ្នាំ២០២៤" or "ឆ្នាំ2024"
            val yearRegex = Regex("ឆ្នាំ\\s*([០-៩0-9]{4})")
            // Regex to match Roman numeral categories like "I. ", "II. ", "IV. "
            val categoryRegex = Regex("^[IVX]+\\..*")

            // Skip the standard header at row 0
            for (i in 1 until rows.size) {
                val row = rows[i]

                val col0 = row.getOrNull(0)?.trim() ?: ""
                val col1 = row.getOrNull(1)?.trim() ?: ""
                val col2 = row.getOrNull(2)?.trim() ?: ""
                val col3 = row.getOrNull(3)?.trim() ?: ""
                val col4 = row.getOrNull(4)?.trim() ?: ""
                val col5 = row.getOrNull(5)?.trim() ?: ""

                // 1. Detect Year Grouping Header
                if (col0.contains("ឆ្នាំ") && col1.isEmpty()) {
                    val yearMatch = yearRegex.find(col0)
                    if (yearMatch != null) {
                        val parsedYear = parseKhmerInt(yearMatch.groupValues[1])
                        if (parsedYear != null) currentYear = parsedYear
                    }
                    continue
                }

                // 2. Detect Roman Numeral Category Header
                if (col0.matches(categoryRegex) && col1.isEmpty()) {
                    currentCategory = col0
                    continue
                }

                // 3. Extract Actual Media Data
                val isIgnoredText = col0.isEmpty() || col0 == "ល.រ" || col0 == "-"
                if (!isIgnoredText) {
                    if (col1.isEmpty()) continue

                    records.add(
                        Nis11A1MediaRecord(
                            year = currentYear,
                            category = currentCategory,
                            serialNo = col0,
                            mediaType = col1,
                            ppCount = col2,
                            relayCount = col3,
                            totalCount = col4,
                            remarks = col5
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis11A1MediaRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_11a1_media_statistics 
            (year, category, serial_no, media_type, pp_count, relay_count, total_count, remarks, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, category, serial_no, upload_id) DO UPDATE SET
                media_type = EXCLUDED.media_type,
                pp_count = EXCLUDED.pp_count,
                relay_count = EXCLUDED.relay_count,
                total_count = EXCLUDED.total_count,
                remarks = EXCLUDED.remarks
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setInt(1, r.year)
                stmt.setString(2, r.category)
                stmt.setString(3, r.serialNo)
                stmt.setString(4, r.mediaType)
                stmt.setString(5, r.ppCount)
                stmt.setString(6, r.relayCount)
                stmt.setString(7, r.totalCount)
                stmt.setString(8, r.remarks)
                stmt.setString(9, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
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