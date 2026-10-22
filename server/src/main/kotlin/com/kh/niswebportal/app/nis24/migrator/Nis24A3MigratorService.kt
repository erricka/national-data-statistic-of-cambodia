package com.kh.niswebportal.app.nis24.migrator

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

data class Nis24A3GbvRecord(
    val serialNo: String,
    val caseDate: String?,
    val violentType: String?,
    val placeCase: String?,
    val serviceFb: String?,
    val serviceTelegram: String?,
    val serviceDirect: String?,
    val victimAge: String?,
    val victimHhStatus: String?,
    val interventionService: String?,
    val interventionDispatch: String?,
    val interventionOther: String?
)

@Service
class Nis24A3MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_24a3"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 24A3 import." }

        val records = file.inputStream.use { parseCsv24A3(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv24A3(inputStream: InputStream): List<Nis24A3GbvRecord> {
        val records = mutableListOf<Nis24A3GbvRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            var codeRowIndex = -1
            val columnMapping = mutableMapOf<String, Int>()

            for (i in 0 until rows.size) {
                val row = rows[i]

                // 1. Scan dynamically for the exact row containing the system codes
                if (codeRowIndex == -1) {
                    var foundCode = false
                    for (c in 0 until row.size()) {
                        val valStr = row.getOrNull(c)?.trim() ?: ""
                        if (valStr.contains("24A3_") || valStr.contains("224A3_")) { // Matches codes including Excel typos
                            foundCode = true
                            when {
                                valStr.contains("Date") -> columnMapping["date"] = c
                                valStr.contains("Violent_Type") -> columnMapping["violent_type"] = c
                                valStr.contains("place_case") -> columnMapping["place_case"] = c
                                valStr.contains("Svice_FB") -> columnMapping["svice_fb"] = c
                                valStr.contains("Svice_Telgram") -> columnMapping["svice_telegram"] = c
                                valStr.contains("Svice_Direct") -> columnMapping["svice_direct"] = c
                                valStr.contains("Victim_Age") -> columnMapping["victim_age"] = c
                                valStr.contains("Victim_hh_status") -> columnMapping["victim_hh_status"] = c
                                valStr.contains("Intvent_svice") -> columnMapping["intvent_svice"] = c
                                valStr.contains("Intvent_dispatch") -> columnMapping["intvent_dispatch"] = c
                                valStr.contains("Intvent_other") -> columnMapping["intvent_other"] = c
                            }
                        }
                    }
                    if (foundCode) {
                        codeRowIndex = i
                        continue
                    }
                }

                // 2. Process log entries
                if (codeRowIndex != -1 && i > codeRowIndex) {
                    val caseDate = row.getSafeValue(columnMapping["date"])
                    val violentType = row.getSafeValue(columnMapping["violent_type"])

                    // Only map rows that contain actual data, skipping empty trailing rows
                    if (caseDate != null || violentType != null) {
                        records.add(
                            Nis24A3GbvRecord(
                                serialNo = row.getOrNull(0)?.trim() ?: "",
                                caseDate = caseDate,
                                violentType = violentType,
                                placeCase = row.getSafeValue(columnMapping["place_case"]),
                                serviceFb = row.getSafeValue(columnMapping["svice_fb"]),
                                serviceTelegram = row.getSafeValue(columnMapping["svice_telegram"]),
                                serviceDirect = row.getSafeValue(columnMapping["svice_direct"]),
                                victimAge = row.getSafeValue(columnMapping["victim_age"]),
                                victimHhStatus = row.getSafeValue(columnMapping["victim_hh_status"]),
                                interventionService = row.getSafeValue(columnMapping["intvent_svice"]),
                                interventionDispatch = row.getSafeValue(columnMapping["intvent_dispatch"]),
                                interventionOther = row.getSafeValue(columnMapping["intvent_other"])
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis24A3GbvRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_24a3_gbv_services 
            (serial_no, case_date, violent_type, place_case, service_fb, service_telegram, service_direct, 
             victim_age, victim_hh_status, intervention_service, intervention_dispatch, intervention_other, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.serialNo)
                stmt.setString(2, r.caseDate)
                stmt.setString(3, r.violentType)
                stmt.setString(4, r.placeCase)
                stmt.setString(5, r.serviceFb)
                stmt.setString(6, r.serviceTelegram)
                stmt.setString(7, r.serviceDirect)
                stmt.setString(8, r.victimAge)
                stmt.setString(9, r.victimHhStatus)
                stmt.setString(10, r.interventionService)
                stmt.setString(11, r.interventionDispatch)
                stmt.setString(12, r.interventionOther)
                stmt.setString(13, uploadId)
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