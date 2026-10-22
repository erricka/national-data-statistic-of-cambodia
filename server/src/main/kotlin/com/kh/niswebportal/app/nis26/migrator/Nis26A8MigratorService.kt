package com.kh.niswebportal.app.nis26.migrator

import com.kh.niswebportal.core.util.KhmerStringUtils
import com.kh.niswebportal.core.util.getOrNull
import com.kh.niswebportal.core.util.setNullableDouble
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

data class Nis26A8CaseRecord(
    val blockCode: String,
    val blockName: String,
    val serialNo: String,
    val description: String,
    val year: Int,
    val receivedCases: Double?,
    val solvedCases: Double?,
    val resolutionRate: Double?
)

@Service
class Nis26A8MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_26a8"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 26a8 import." }

        val records = file.inputStream.use { parseCsv26A8(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv26A8(inputStream: InputStream): List<Nis26A8CaseRecord> {
        val records = mutableListOf<Nis26A8CaseRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            var currentBlockCode = ""
            var currentBlockName = ""

            val yearMapping = mutableMapOf<Int, Int>() // colIndex -> Year
            val typeMapping = mutableMapOf<Int, String>() // colIndex -> 'received', 'solved', or 'rate'

            for (i in 0 until rows.size) {
                val row = rows[i]
                val col0 = row.getOrNull(0)?.trim() ?: ""
                val col1 = row.getOrNull(1)?.trim() ?: ""

                // 1. Detect Block Header (e.g., 26A8_1, 26A8_2)
                if (col0.matches(Regex("^26A8_[1-9]$"))) {
                    currentBlockCode = col0
                    currentBlockName = col1.removePrefix(".") // Clean accidental prefixes from Excel
                    continue
                }

                // 2. Detect Year Mappings
                if (col0 == "លរ") {
                    yearMapping.clear()
                    var activeYear: Int? = null
                    for (c in 2 until row.size()) {
                        val valStr = row.getOrNull(c)?.trim() ?: ""
                        val match = Regex("(20\\d\\d|២០[០-៩]{2})").find(valStr)
                        if (match != null) {
                            val yearStr = match.groupValues[1].map {
                                mapOf('០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
                                    '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9')[it] ?: it
                            }.joinToString("")
                            activeYear = yearStr.toInt()
                        }
                        if (activeYear != null) yearMapping[c] = activeYear
                    }
                    continue
                }

                // 3. Detect Variable Code Mappings (RECEIVED, SOLVED, RER)
                if (col2ContainsCodes(row) && yearMapping.isNotEmpty()) {
                    typeMapping.clear()
                    for ((c, _) in yearMapping) {
                        val codeVal = row.getOrNull(c)?.trim() ?: ""
                        if (codeVal.contains("RECEIVED")) typeMapping[c] = "received"
                        else if (codeVal.contains("SOLVED")) typeMapping[c] = "solved"
                        else if (codeVal.contains("RER")) typeMapping[c] = "rate"
                    }
                    continue
                }

                // 4. Extract Data Rows
                // Standard rows start with an integer Serial Number (1, 2, 3...)
                if (col0.matches(Regex("^[0-9]+$")) && currentBlockCode.isNotEmpty() && yearMapping.isNotEmpty()) {
                    val description = col1
                    if (description.isBlank()) continue

                    // Group by Year to bundle the 3 columns together
                    val yearData = mutableMapOf<Int, MutableMap<String, Double?>>()

                    for ((c, year) in yearMapping) {
                        if (!yearData.containsKey(year)) {
                            yearData[year] = mutableMapOf("received" to null, "solved" to null, "rate" to null)
                        }

                        val colType = typeMapping[c]
                        if (colType != null) {
                            yearData[year]!![colType] = KhmerStringUtils.parseKhmerDouble(row.getOrNull(c))
                        }
                    }

                    // Create records
                    for ((year, data) in yearData) {
                        records.add(
                            Nis26A8CaseRecord(
                                blockCode = currentBlockCode,
                                blockName = currentBlockName,
                                serialNo = col0,
                                description = description,
                                year = year,
                                receivedCases = data["received"],
                                solvedCases = data["solved"],
                                resolutionRate = data["rate"]
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun col2ContainsCodes(row: CSVRecord): Boolean {
        for (i in 2 until row.size()) {
            val text = row.getOrNull(i)?.trim() ?: ""
            if (text.contains("RECEIVED") || text.contains("SOLVED") || text.contains("RER")) return true
        }
        return false
    }

    private fun insertBatch(connection: Connection, records: List<Nis26A8CaseRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_26a8_campaign_cases 
            (block_code, block_name, serial_no, description, year, received_cases, solved_cases, resolution_rate, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (block_code, description, year, upload_id) DO UPDATE SET
                block_name = EXCLUDED.block_name,
                serial_no = EXCLUDED.serial_no,
                received_cases = EXCLUDED.received_cases,
                solved_cases = EXCLUDED.solved_cases,
                resolution_rate = EXCLUDED.resolution_rate
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.blockCode)
                stmt.setString(2, r.blockName)
                stmt.setString(3, r.serialNo)
                stmt.setString(4, r.description)
                stmt.setInt(5, r.year)
                stmt.setNullableDouble( 6, r.receivedCases)
                stmt.setNullableDouble( 7, r.solvedCases)
                stmt.setNullableDouble( 8, r.resolutionRate)
                stmt.setString(9, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    

    

    
}