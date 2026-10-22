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
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Types
import javax.sql.DataSource

data class Nis22A1Record(
    val metricCode: String,
    val itemNo: String?,
    val descriptionEnglish: String,
    val category: String?,
    val year: Int,
    val amount: Long?
)

private data class Nis22A1YearColumnMapping(
    val year: Int,
    val colIndex: Int
)

@Service
class Nis22A1MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_22a1"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 22a1 import." }

        val records = file.inputStream.use { parseCsv22A1(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv22A1(inputStream: InputStream): List<Nis22A1Record> {
        val records = mutableListOf<Nis22A1Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.isEmpty()) return emptyList()

            // 1. Locate row containing year headers (Row index 2 in 22a1 template)
            var yearHeaderRowIndex = -1
            for (i in rows.indices) {
                val cellId = rows[i].getOrNull(0)?.trim()
                if (cellId.equals("ID", ignoreCase = true) || cellId.equals("កូដ", ignoreCase = true)) {
                    yearHeaderRowIndex = i
                    break
                }
            }

            if (yearHeaderRowIndex == -1) return emptyList()

            // 2. Map year columns starting from column index 4
            val yearMappings = detectYearMappings(rows[yearHeaderRowIndex])
            if (yearMappings.isEmpty()) return emptyList()

            // 3. Track active category section (Domestic Sending vs International Sending)
            var currentCategory = "General"

            for (rowIndex in (yearHeaderRowIndex + 1) until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 2) continue

                val metricCode = row.getOrNull(0)?.trim() ?: ""
                val col1 = row.getOrNull(1)?.trim() ?: ""
                val col2 = row.getOrNull(2)?.trim() ?: ""

                if (metricCode.isBlank() || metricCode.startsWith("Source:", ignoreCase = true)) continue

                // Update category context for section headers
                if (metricCode == "22a1_1_doms_sending" || col1.contains("Domestic", ignoreCase = true)) {
                    currentCategory = "Domestic Sending"
                    continue
                } else if (metricCode == "22a1_2_inter_sending" || col1.contains("International", ignoreCase = true)) {
                    currentCategory = "International Sending"
                    continue
                } else if (metricCode == "22a1_sending") {
                    currentCategory = "Sending"
                    continue
                }

                val itemNo = if (col1.matches(Regex("^\\d+$"))) col1 else null
                val descriptionEnglish = when {
                    col2.isNotBlank() -> col2
                    col1.isNotBlank() && itemNo == null -> col1
                    else -> metricCode
                }

                for (mapping in yearMappings) {
                    val rawValue = row.getOrNull(mapping.colIndex)
                    val amount = parseKhmerLong(rawValue)

                    records.add(
                        Nis22A1Record(
                            metricCode = metricCode,
                            itemNo = itemNo,
                            descriptionEnglish = descriptionEnglish,
                            category = currentCategory,
                            year = mapping.year,
                            amount = amount
                        )
                    )
                }
            }
        }
        return records
    }

    private fun detectYearMappings(headerRow: CSVRecord): List<Nis22A1YearColumnMapping> {
        val mappings = mutableListOf<Nis22A1YearColumnMapping>()

        for (colIndex in 4 until headerRow.size()) {
            val cellText = headerRow.getOrNull(colIndex)?.trim() ?: ""
            val year = parseKhmerLong(cellText)?.toInt()

            if (year != null && year in 2000..2099) {
                mappings.add(Nis22A1YearColumnMapping(year = year, colIndex = colIndex))
            }
        }
        return mappings
    }

    private fun insertBatch(connection: Connection, records: List<Nis22A1Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_22a1_postal_services 
            (metric_code, item_no, description_english, category, year, amount, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (metric_code, year, upload_id) DO UPDATE SET
                item_no = EXCLUDED.item_no,
                description_english = EXCLUDED.description_english,
                category = EXCLUDED.category,
                amount = EXCLUDED.amount
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.metricCode)
                stmt.setString(2, r.itemNo)
                stmt.setString(3, r.descriptionEnglish)
                stmt.setString(4, r.category)
                stmt.setInt(5, r.year)
                setNullableLong(stmt, 6, r.amount)
                stmt.setString(7, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun setNullableLong(stmt: PreparedStatement, index: Int, value: Long?) {
        if (value != null) stmt.setLong(index, value) else stmt.setNull(index, Types.BIGINT)
    }

    private fun parseKhmerLong(text: String?): Long? {
        if (text.isNullOrBlank() || text.trim() == "-") return null
        val khmerToLatinDigits = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
        val normalized = text.map { khmerToLatinDigits[it] ?: it }.joinToString("")
        val digitsOnly = normalized.replace(Regex("[^0-9]"), "")
        return digitsOnly.toLongOrNull()
    }

    
}