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

data class Nis08A5InspectionRecord(
    val levelType: String,
    val provinceName: String,
    val sectorName: String,
    val serialNo: String,
    val description: String,
    val location: String
)

@Service
class Nis08A5MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_08a5"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 08a5 import." }

        val records = file.inputStream.use { parseCsv08A5(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv08A5(inputStream: InputStream): List<Nis08A5InspectionRecord> {
        val records = mutableListOf<Nis08A5InspectionRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 2) return emptyList()

            var currentLevel = ""
            var currentProvince = ""
            var currentSector = ""

            // Skip header at index 0
            for (i in 1 until rows.size) {
                val row = rows[i]

                val col0 = row.getOrNull(0)?.trim() ?: ""
                val col1 = row.getOrNull(1)?.trim() ?: ""
                // Location is at index 5 (Column F)
                val col5 = row.getOrNull(5)?.trim() ?: ""

                // 1. Detect Hierarchical Headers (Level, Province, Sector)
                // Headers are defined by having data in Col 0, but Col 1 and Col 5 are empty.
                if (col1.isEmpty() && col5.isEmpty() && col0.isNotEmpty()) {
                    if (col0.startsWith("ក.") || col0.startsWith("ខ.")) {
                        currentLevel = col0
                        if (col0.startsWith("ក.")) {
                            currentProvince = "" // Reset province for Central Level
                        }
                    } else if (col0.startsWith("វិស័យ")) {
                        currentSector = col0
                    } else {
                        // If it's not a Level or Sector, it must be a Province header
                        currentProvince = col0
                    }
                }
                // 2. Detect Actual Inspection Records
                else if (col0.isNotEmpty() && col0 != "-") {
                    if (col1 == "-" || col1.isEmpty()) continue

                    records.add(
                        Nis08A5InspectionRecord(
                            levelType = currentLevel,
                            provinceName = currentProvince,
                            sectorName = currentSector,
                            serialNo = col0,
                            description = col1,
                            location = col5
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis08A5InspectionRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_08a5_inspection_registry 
            (level_type, province_name, sector_name, serial_no, description, location, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (level_type, province_name, sector_name, serial_no, upload_id) DO UPDATE SET
                description = EXCLUDED.description,
                location = EXCLUDED.location
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.levelType)
                stmt.setString(2, r.provinceName)
                stmt.setString(3, r.sectorName)
                stmt.setString(4, r.serialNo)
                stmt.setString(5, r.description)
                stmt.setString(6, r.location)
                stmt.setString(7, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    
}