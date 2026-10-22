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
class Nis16d1LiteracyMigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_16d1_lit"

    private companion object {
        private val KHMER_TO_LATIN_MAP = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for nis_16d1_lit import." }

        return file.inputStream.use { inputStream ->
            parseAndSaveCsv(inputStream, uploadId)
        }
    }

    private fun parseAndSaveCsv(inputStream: InputStream, uploadId: String): Int {
        InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 22) return 0

            dataSource.connection.use { connection ->
                var totalRecords = 0

                val sql = """
                    INSERT INTO nis_16d1_literacy (
                        budget_year, class_count, teacher_total, teacher_female, 
                        enrollment_total, enrollment_female, graduation_total, upload_id
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (budget_year, upload_id) DO UPDATE SET
                        class_count = EXCLUDED.class_count,
                        teacher_total = EXCLUDED.teacher_total,
                        teacher_female = EXCLUDED.teacher_female,
                        enrollment_total = EXCLUDED.enrollment_total,
                        enrollment_female = EXCLUDED.enrollment_female,
                        graduation_total = EXCLUDED.graduation_total
                """.trimIndent()

                connection.prepareStatement(sql).use { stmt ->
                    // Data rows for nis_16d1_lit start around row index 23 onwards in the CSV file
                    for (i in 23 until rows.size) {
                        val row = rows[i]
                        val yearStr = row.getOrNull(2)?.trim()?.replace(".0", "") ?: ""
                        val cleanYear = yearStr.toLatinDigits()

                        if (!cleanYear.matches(Regex("20\\d{2}|19\\d{2}"))) {
                            if (cleanYear.contains("ប្រភព") || cleanYear.isBlank()) break
                            continue
                        }

                        stmt.setString(1, cleanYear)
                        stmt.setString(2, cleanValue(row.getOrNull(3))) // class_count
                        stmt.setString(3, cleanValue(row.getOrNull(4))) // teacher_total
                        stmt.setString(4, cleanValue(row.getOrNull(5))) // teacher_female
                        stmt.setString(5, cleanValue(row.getOrNull(6))) // enrollment_total
                        stmt.setString(6, cleanValue(row.getOrNull(7))) // enrollment_female
                        stmt.setString(7, cleanValue(row.getOrNull(8))) // graduation_total
                        stmt.setString(8, uploadId)

                        stmt.addBatch()
                        totalRecords++
                    }
                    stmt.executeBatch()
                }
                return totalRecords
            }
        }
    }

    private fun cleanValue(value: String?): String? {
        val cleaned = value?.trim()?.replace(",", "")?.replace(".0", "") ?: return null
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