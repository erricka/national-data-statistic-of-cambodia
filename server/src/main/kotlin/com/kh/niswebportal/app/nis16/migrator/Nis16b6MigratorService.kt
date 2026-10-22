package com.kh.niswebportal.app.nis16.migrator

import com.kh.niswebportal.core.strategy.NisMigratorStrategy
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.io.InputStreamReader
import javax.sql.DataSource

@Service
class Nis16b6MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_16b6"

    private companion object {
        private val KHMER_TO_LATIN_MAP = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for nis_16b6 import." }

        return file.inputStream.use { inputStream ->
            parseAndSaveCsv(inputStream, uploadId)
        }
    }

    private fun parseAndSaveCsv(inputStream: InputStream, uploadId: String): Int {
        InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 8) return 0

            // ទាញយកឆ្នាំដោយស្វ័យប្រវត្តិពីជួរដេកទី 7 (Year Header Row)
            val yearHeaderRow = rows.getOrNull(7) ?: return 0
            val yearMapping = mutableMapOf<Int, String>()

            for (colIdx in 2 until yearHeaderRow.size()) {
                val yearVal = yearHeaderRow.get(colIdx)?.trim()
                if (!yearVal.isNullOrBlank() && yearVal.lowercase() != "nan") {
                    yearMapping[colIdx] = cleanYear(yearVal)
                }
            }

            dataSource.connection.use { connection ->
                // សម្អាតទិន្នន័យចាស់មុនពេលបញ្ចូលថ្មី
//                connection.prepareStatement("DELETE FROM nis_16b6_majors_yearly").use { it.executeUpdate() }
//                connection.prepareStatement("DELETE FROM nis_16b6_majors_statistics").use { it.executeUpdate() }
//
//                connection.prepareStatement("DELETE FROM datasource_history WHERE source_id = ?").use { stmt ->
//                    stmt.setString(1, sourceId)
//                    stmt.executeUpdate()
//                }

                var currentStudyLevel = "កម្រិតឧត្តមសិក្សា"
                var totalRecords = 0

                val majorSql = """
                    INSERT INTO nis_16b6_majors_statistics (major_code, major_name, study_level, upload_id)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (major_code, upload_id) DO UPDATE SET 
                        major_name = EXCLUDED.major_name,
                        study_level = EXCLUDED.study_level
                """.trimIndent()

                val yearSql = """
                    INSERT INTO nis_16b6_majors_yearly (major_code, academic_year, student_count, upload_id)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (major_code, academic_year, upload_id) DO UPDATE SET
                        student_count = EXCLUDED.student_count
                """.trimIndent()

                connection.prepareStatement(majorSql).use { majorStmt ->
                    connection.prepareStatement(yearSql).use { yearStmt ->

                        for (i in 8 until rows.size) {
                            val row = rows[i]
                            val col0 = row.getOrNull(0)?.trim() ?: ""
                            val col1 = row.getOrNull(1)?.trim() ?: ""

                            if (col0.isBlank() || col0.lowercase() == "nan") continue

                            if (col0.contains("16b6_") || col0.contains("កម្រិត")) {
                                if (col1.isNotBlank() && col1.lowercase() != "nan") {
                                    currentStudyLevel = col1
                                }
                                continue
                            }

                            if (col0.contains("ប្រភព") || col0.contains("Source")) break

                            // បញ្ចូលព័ត៌មានជំនាញ
                            majorStmt.setString(1, col0)
                            majorStmt.setString(2, col1)
                            majorStmt.setString(3, currentStudyLevel)
                            majorStmt.setString(4, uploadId)
                            majorStmt.executeUpdate()

                            // បញ្ចូលទិន្នន័យចំនួននិស្សិតតាមឆ្នាំនីមួយៗ
                            val processedYears = mutableSetOf<String>()
                            for ((colIdx, yearStr) in yearMapping) {
                                if (processedYears.contains(yearStr)) continue
                                processedYears.add(yearStr)

                                val countVal = cleanValue(row.getOrNull(colIdx))

                                if (countVal != null) {
                                    yearStmt.setString(1, col0)
                                    yearStmt.setString(2, yearStr)
                                    yearStmt.setString(3, countVal)
                                    yearStmt.setString(4, uploadId)
                                    yearStmt.addBatch()
                                }
                            }
                            totalRecords++
                        }
                        yearStmt.executeBatch()
                    }
                }
                return totalRecords
            }
        }
    }

    private fun cleanYear(value: String?): String {
        return value?.trim()?.replace(" ", "")?.toLatinDigits() ?: ""
    }

    private fun cleanValue(value: String?): String? {
        val cleaned = value?.trim()?.replace(" ", "")?.replace(",", "") ?: return null
        if (cleaned.isBlank() || cleaned == "-" || cleaned.lowercase() == "n.a" || cleaned.lowercase() == "nan") return null
        return cleaned.toLatinDigits()
    }

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}