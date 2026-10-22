package com.kh.niswebportal.app.nis08.migrator

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

data class Nis08A2ComplaintRecord(
    val levelType: String,
    val provinceName: String,
    val serialNo: String,
    val description: String,
    val complaintsCount: String,
    val casesCount: String
)

@Service
class Nis08A2MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_08a2"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 08a2 import." }

        val records = file.inputStream.use { parseCsv08A2(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv08A2(inputStream: InputStream): List<Nis08A2ComplaintRecord> {
        val records = mutableListOf<Nis08A2ComplaintRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 2) return emptyList()

            var currentLevel = ""
            var currentProvince = ""

            // Skip headers at index 0 and 1
            for (i in 2 until rows.size) {
                val row = rows[i]

                val col0 = row.getOrNull(0)?.trim() ?: ""
                val col1 = row.getOrNull(1)?.trim() ?: ""
                // In the original Excel, columns C and D are empty. Data is at E (index 4) and F (index 5)
                val col4 = row.getOrNull(4)?.trim() ?: ""
                val col5 = row.getOrNull(5)?.trim() ?: ""

                // 1. Detect Hierarchical Headers (Level, Province)
                if (col1.isEmpty() && col4.isEmpty() && col5.isEmpty() && col0.isNotEmpty()) {
                    if (col0.contains("ថ្នាក់កណ្តាល") || col0.contains("ថ្នាក់មន្ទីរ")) {
                        currentLevel = col0
                        if (col0.contains("ថ្នាក់កណ្តាល")) {
                            currentProvince = "" // Reset province for Central Level
                        }
                    } else {
                        // If it's not a Level, it must be a Province header
                        currentProvince = col0
                    }
                }
                // 2. Detect Actual Complaint Records
                else if (col0.isNotEmpty() && col0 != "-") {
                    if (col1 == "-" || col1.isEmpty()) continue

                    records.add(
                        Nis08A2ComplaintRecord(
                            levelType = currentLevel,
                            provinceName = currentProvince,
                            serialNo = col0,
                            description = col1,
                            complaintsCount = col4,
                            casesCount = col5
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis08A2ComplaintRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_08a2_complaint_registry 
            (level_type, province_name, serial_no, description, complaints_count, cases_count, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (level_type, province_name, serial_no, upload_id) DO UPDATE SET
                description = EXCLUDED.description,
                complaints_count = EXCLUDED.complaints_count,
                cases_count = EXCLUDED.cases_count
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.levelType)
                stmt.setString(2, r.provinceName)
                stmt.setString(3, r.serialNo)
                stmt.setString(4, r.description)
                stmt.setString(5, r.complaintsCount)
                stmt.setString(6, r.casesCount)
                stmt.setString(7, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    
}