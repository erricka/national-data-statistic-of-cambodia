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
class Nis16b3MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_16b3"

    private companion object {
        private val KHMER_TO_LATIN_MAP = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for nis_16b3 import." }

        return file.inputStream.use { inputStream ->
            parseAndSaveCsv(inputStream, uploadId)
        }
    }

    private fun parseAndSaveCsv(inputStream: InputStream, uploadId: String): Int {
        InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 11) return 0

            // ទាញយកឆ្នាំដោយស្វ័យប្រវត្តិពីជួរដេកទី 8 (Year Header Row)
            val yearHeaderRow = rows.getOrNull(8) ?: return 0
            val yearMapping = mutableMapOf<Int, String>()

            for (colIdx in 2 until yearHeaderRow.size()) {
                val yearVal = yearHeaderRow.get(colIdx)?.trim()
                if (!yearVal.isNullOrBlank() && yearVal.lowercase() != "nan") {
                    yearMapping[colIdx] = cleanYear(yearVal)
                }
            }

            dataSource.connection.use { connection ->
                // សម្អាតទិន្នន័យចាស់មុនពេលបញ្ចូលថ្មី
//                connection.prepareStatement("DELETE FROM nis_16b3_nonformal_education_yearly").use { it.executeUpdate() }
//                connection.prepareStatement("DELETE FROM nis_16b3_nonformal_education_statistics").use { it.executeUpdate() }
//
//                connection.prepareStatement("DELETE FROM datasource_history WHERE source_id = ?").use { stmt ->
//                    stmt.setString(1, sourceId)
//                    stmt.executeUpdate()
//                }

                var currentSector = "PUBLIC"
                var totalRecords = 0

                val instSql = """
                    INSERT INTO nis_16b3_nonformal_education_statistics (institution_code, institution_name, sector, upload_id)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (institution_code, upload_id) DO UPDATE SET 
                        institution_name = EXCLUDED.institution_name,
                        sector = EXCLUDED.sector
                """.trimIndent()

                val yearSql = """
                    INSERT INTO nis_16b3_nonformal_education_yearly (institution_code, academic_year, total_count, female_count, upload_id)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (institution_code, academic_year, upload_id) DO UPDATE SET
                        total_count = EXCLUDED.total_count,
                        female_count = EXCLUDED.female_count
                """.trimIndent()

                connection.prepareStatement(instSql).use { instStmt ->
                    connection.prepareStatement(yearSql).use { yearStmt ->

                        for (i in 11 until rows.size) {
                            val row = rows[i]
                            val col0 = row.getOrNull(0)?.trim() ?: ""
                            val col1 = row.getOrNull(1)?.trim() ?: ""

                            if (col0.isBlank() || col0.lowercase() == "nan") continue

                            if (col0.contains("16b3_pub") || col0.contains("រដ្ឋ")) {
                                currentSector = "PUBLIC"
                                continue
                            } else if (col0.contains("16b3_pri") || col0.contains("ឯកជន")) {
                                currentSector = "PRIVATE"
                                continue
                            } else if (col0.contains("16b3_total") || col0.contains("សរុប")) {
                                currentSector = "TOTAL"
                            }

                            if (col0.contains("ប្រភព") || col0.contains("Source")) break

                            // បញ្ចូលព័ត៌មានគ្រឹះស្ថាន
                            instStmt.setString(1, col0)
                            instStmt.setString(2, col1)
                            instStmt.setString(3, currentSector)
                            instStmt.setString(4, uploadId)
                            instStmt.executeUpdate()

                            // បញ្ចូលទិន្នន័យតាមឆ្នាំនីមួយៗ (សរុប និងស្រី)
                            val processedYears = mutableSetOf<String>()
                            for ((colIdx, yearStr) in yearMapping) {
                                if (processedYears.contains(yearStr)) continue
                                processedYears.add(yearStr)

                                val totalVal = cleanValue(row.getOrNull(colIdx))
                                val femaleVal = cleanValue(row.getOrNull(colIdx + 1))

                                if (totalVal != null || femaleVal != null) {
                                    yearStmt.setString(1, col0)
                                    yearStmt.setString(2, yearStr)
                                    yearStmt.setString(3, totalVal)
                                    yearStmt.setString(4, femaleVal)
                                    yearStmt.setString(5, uploadId)
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