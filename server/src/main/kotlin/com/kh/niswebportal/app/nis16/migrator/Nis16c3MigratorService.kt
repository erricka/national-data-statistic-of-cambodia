package com.kh.niswebportal.app.nis16.migrator

import com.kh.niswebportal.core.strategy.NisMigratorStrategy
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.io.InputStreamReader
import javax.sql.DataSource

@Service
class Nis16c3MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_16c3"

    private companion object {
        private val KHMER_TO_LATIN_MAP = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for nis_16c3 import." }

        return file.inputStream.use { inputStream ->
            parseAndSaveCsv(inputStream, uploadId)
        }
    }

    private fun parseAndSaveCsv(inputStream: InputStream, uploadId: String): Int {
        InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 8) return 0

            val headerRow = rows[0] // Row 8 is header containing years
            val years = mutableListOf<Pair<Int, String>>() // Pair of (Column Index, Year String)

            for (colIdx in 2 until headerRow.size()) {
                val yearStr = headerRow.getOrNull(colIdx)?.trim()?.replace(".0", "") ?: ""
                val cleanYear = yearStr.toLatinDigits()
                if (cleanYear.matches(Regex("20\\d{2}|19\\d{2}"))) {
                    years.add(Pair(colIdx, cleanYear))
                }
            }

            dataSource.connection.use { connection ->
                var totalRecords = 0

                val sql = """
                    INSERT INTO nis_16c3_sports_statistics (
                        nis_code, budget_year, category_name, indicator_value, upload_id
                    ) VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (nis_code, budget_year, upload_id) DO UPDATE SET
                        category_name = EXCLUDED.category_name,
                        indicator_value = EXCLUDED.indicator_value
                """.trimIndent()

                connection.prepareStatement(sql).use { stmt ->
                    // Iterate through data rows (Row 9 onwards)
                    for (i in 1 until rows.size) {
                        val row = rows[i]
                        val nisCode = row.getOrNull(0)?.trim() ?: ""
                        if (nisCode.isBlank() || nisCode.lowercase() == "nan") continue
                        if (nisCode.contains("ប្រភព") || nisCode.contains("Source")) break

                        val categoryName = row.getOrNull(1)?.trim() ?: ""

                        // For each year column, insert a vertical row
                        for ((colIdx, year) in years) {
                            val value = cleanValue(row.getOrNull(colIdx))

                            // Unique compound key construct for conflict check: nisCode_year
                            val uniqueNisCodeYear = "${nisCode}_$year"

                            stmt.setString(1, uniqueNisCodeYear)         // nis_code combined with year for unique constraint
                            stmt.setString(2, year)                      // budget_year (Vertical)
                            stmt.setString(3, categoryName)              // category_name (ជំនាញសិក្សា / សូចនាករ)
                            stmt.setString(4, value)                     // indicator_value
                            stmt.setString(5, uploadId)

                            stmt.addBatch()
                            totalRecords++
                        }
                    }
                    stmt.executeBatch()
                }
                return totalRecords
            }
        }
    }

    private fun cleanValue(value: String?): String? {
        val cleaned = value?.trim()?.replace(",", "") ?: return null
        if (cleaned.isBlank() || cleaned == "-" || cleaned.lowercase() == "n.a" || cleaned.lowercase() == "nan") return null
        return cleaned.toLatinDigits()
    }

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }

    private fun org.apache.commons.csv.CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}