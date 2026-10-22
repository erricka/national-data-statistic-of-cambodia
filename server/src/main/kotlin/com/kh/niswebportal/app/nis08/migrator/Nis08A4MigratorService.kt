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

data class Nis08A4DisseminationRecord(
    val levelType: String,
    val groupName: String,
    val serialNo: String,
    val activityDate: String,
    val location1: String,
    val location2: String,
    val topicDesc: String,
    val metricTotal: String,
    val metricFemale: String
)

@Service
class Nis08A4MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_08a4"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 08a4 import." }

        val records = file.inputStream.use { parseCsv08A4(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv08A4(inputStream: InputStream): List<Nis08A4DisseminationRecord> {
        val records = mutableListOf<Nis08A4DisseminationRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 2) return emptyList()

            var currentLevel = ""
            var currentGroup = ""

            for (i in 2 until rows.size) {
                val row = rows[i]
                val col0 = row.getOrNull(0)?.trim() ?: ""
                val col1 = row.getOrNull(1)?.trim() ?: ""

                // 1. Detect Level Headers
                if (col0.startsWith("ក.") || col0.startsWith("ខ.")) {
                    currentLevel = col0
                    currentGroup = ""
                    continue
                }

                // 2. Detect Group Headers (Categories 1-5 or Province Names)
                val isCategory = col0.startsWith("១.") || col0.startsWith("២.") || col0.startsWith("៣.") ||
                        col0.startsWith("៤.") || col0.startsWith("៥.")
                val isProvince = currentLevel.startsWith("ខ.") &&
                        (col0.contains("ខេត្ត") || col0.contains("រាជធានី")) && col0.length < 50

                if (isCategory || isProvince) {
                    if (col1.isEmpty() || col1.equals("nan", ignoreCase = true)) {
                        currentGroup = col0
                        continue
                    }
                }

                // 3. Extract Data Rows
                val isIgnoredText = col0.isEmpty() || col0 == "សរុបចំនួន" || col0 == "ល.រ" || col0 == "ល .រ"

                if (!isIgnoredText) {
                    var activityDate = col1
                    // Clean Excel datetime artifacts if they exist
                    if (activityDate.contains("00:00:00")) {
                        activityDate = activityDate.split(" ")[0]
                    }

                    records.add(
                        Nis08A4DisseminationRecord(
                            levelType = currentLevel,
                            groupName = currentGroup,
                            serialNo = col0,
                            activityDate = activityDate,
                            location1 = row.getOrNull(2)?.trim() ?: "",
                            location2 = row.getOrNull(3)?.trim() ?: "",
                            topicDesc = row.getOrNull(4)?.trim() ?: "",
                            metricTotal = row.getOrNull(5)?.trim() ?: "",
                            metricFemale = row.getOrNull(6)?.trim() ?: ""
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis08A4DisseminationRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_08a4_dissemination_registry 
            (level_type, group_name, serial_no, activity_date, location_1, location_2, topic_desc, metric_total, metric_female, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (level_type, group_name, serial_no, upload_id) DO UPDATE SET
                activity_date = EXCLUDED.activity_date,
                location_1 = EXCLUDED.location_1,
                location_2 = EXCLUDED.location_2,
                topic_desc = EXCLUDED.topic_desc,
                metric_total = EXCLUDED.metric_total,
                metric_female = EXCLUDED.metric_female
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.levelType)
                stmt.setString(2, r.groupName)
                stmt.setString(3, r.serialNo)
                stmt.setString(4, r.activityDate)
                stmt.setString(5, r.location1)
                stmt.setString(6, r.location2)
                stmt.setString(7, r.topicDesc)
                stmt.setString(8, r.metricTotal)
                stmt.setString(9, r.metricFemale)
                stmt.setString(10, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    
}