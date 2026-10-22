package com.kh.niswebportal.app.nis26.migrator

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

data class Nis26A7InspectionRecord(
    val courtCode: String,
    val institutionName: String,
    val year: Int,
    val judgeCount: String?,
    val prosecutorCount: String?,
    val secretariatCount: String?,
    val clerkCount: String?,
    val bailiffCount: String?,
    val lawyerCount: String?,
    val plaintiffCount: String?,
    val totalCount: String?
)

@Service
class Nis26A7MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_26a7"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 26a7 import." }

        val records = file.inputStream.use { parseCsv26A7(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv26A7(inputStream: InputStream): List<Nis26A7InspectionRecord> {
        val records = mutableListOf<Nis26A7InspectionRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            var currentYear: Int? = null
            val colMapping = mutableMapOf<String, Int>()

            for (i in 0 until rows.size) {
                val row = rows[i]
                val col0 = row.getOrNull(0)?.trim() ?: ""
                val col2 = row.getOrNull(2)?.trim() ?: ""

                // 1. Detect Year Header Block
                val yearMatch = Regex("(20\\d\\d|២០[០-៩]{2})").find(col2)
                if (yearMatch != null && (col2.contains("ឆ្នាំ") || col2.contains("ប្រចាំឆ្នាំ"))) {
                    val yearStr = yearMatch.groupValues[1].map {
                        mapOf('០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
                            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9')[it] ?: it
                    }.joinToString("")
                    currentYear = yearStr.toInt()
                    continue
                }

                // 2. Detect Code Mapping Row
                if (col0 == "CODE") {
                    colMapping.clear()
                    for (idx in 3 until row.size()) {
                        val valStr = row.getOrNull(idx)?.trim() ?: ""
                        if (valStr.contains("_Judge")) colMapping["judge"] = idx
                        else if (valStr.contains("_Prosecutor")) colMapping["prosecutor"] = idx
                        else if (valStr.contains("_Secretariate")) colMapping["secretariat"] = idx
                        else if (valStr.contains("_Clerk")) colMapping["clerk"] = idx
                        else if (valStr.contains("_Bailiff")) colMapping["bailiff"] = idx
                        else if (valStr.contains("_Lawyer")) colMapping["lawyer"] = idx
                        else if (valStr.contains("_Plaintiff")) colMapping["plaintiff"] = idx
                        else if (valStr.contains("_T")) colMapping["total"] = idx
                    }
                    continue
                }

                // 3. Extract Data Row
                if (currentYear != null && col0.endsWith("_Court")) {
                    records.add(
                        Nis26A7InspectionRecord(
                            courtCode = col0,
                            institutionName = col2,
                            year = currentYear,
                            judgeCount = row.getSafeValue(colMapping["judge"]),
                            prosecutorCount = row.getSafeValue(colMapping["prosecutor"]),
                            secretariatCount = row.getSafeValue(colMapping["secretariat"]),
                            clerkCount = row.getSafeValue(colMapping["clerk"]),
                            bailiffCount = row.getSafeValue(colMapping["bailiff"]),
                            lawyerCount = row.getSafeValue(colMapping["lawyer"]),
                            plaintiffCount = row.getSafeValue(colMapping["plaintiff"]),
                            totalCount = row.getSafeValue(colMapping["total"])
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis26A7InspectionRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_26a7_court_inspections 
            (court_code, institution_name, year, judge_count, prosecutor_count, secretariat_count, 
             clerk_count, bailiff_count, lawyer_count, plaintiff_count, total_count, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (court_code, year, upload_id) DO UPDATE SET
                institution_name = EXCLUDED.institution_name,
                judge_count = EXCLUDED.judge_count,
                prosecutor_count = EXCLUDED.prosecutor_count,
                secretariat_count = EXCLUDED.secretariat_count,
                clerk_count = EXCLUDED.clerk_count,
                bailiff_count = EXCLUDED.bailiff_count,
                lawyer_count = EXCLUDED.lawyer_count,
                plaintiff_count = EXCLUDED.plaintiff_count,
                total_count = EXCLUDED.total_count
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.courtCode)
                stmt.setString(2, r.institutionName)
                stmt.setInt(3, r.year)
                stmt.setString(4, r.judgeCount)
                stmt.setString(5, r.prosecutorCount)
                stmt.setString(6, r.secretariatCount)
                stmt.setString(7, r.clerkCount)
                stmt.setString(8, r.bailiffCount)
                stmt.setString(9, r.lawyerCount)
                stmt.setString(10, r.plaintiffCount)
                stmt.setString(11, r.totalCount)
                stmt.setString(12, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    
    private fun CSVRecord.getSafeValue(index: Int?): String? {
        if (index == null || index < 0) return null
        val value = this.getOrNull(index)?.trim()
        return if (value.isNullOrBlank() || value == "-") null else value
    }
}