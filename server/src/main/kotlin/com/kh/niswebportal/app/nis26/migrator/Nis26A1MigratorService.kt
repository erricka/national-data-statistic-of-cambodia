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

data class Nis26A1CaseRecord(
    val proCode: String,
    val provinceName: String,
    val caseType: String,
    val itemCode: String,
    val year: Int,
    val numCases: Double?,
    val numCasesAdjudicated: Double?
)

@Service
class Nis26A1MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_26a1"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 26a1 import." }

        val records = file.inputStream.use { parseCsv26A1(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv26A1(inputStream: InputStream): List<Nis26A1CaseRecord> {
        val records = mutableListOf<Nis26A1CaseRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 6) return emptyList()

            // 1. Locate Header Rows (Rows 2 and 3 in index) to map columns to Years & Types
            val rowYear = rows[2]
            val rowType = rows[3]

            data class ColMap(val year: Int, val type: String)
            val columnMapping = mutableMapOf<Int, ColMap>() // colIndex -> ColMap

            var activeYear: Int? = null
            for (c in 4 until rowYear.size()) {
                val valYear = rowYear.getOrNull(c)?.trim() ?: ""

                // Extract Year
                val match = Regex("(20\\d\\d|២០[០-៩]{2})").find(valYear)
                if (match != null) {
                    val yearStr = match.groupValues[1].map {
                        mapOf('០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
                            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9')[it] ?: it
                    }.joinToString("")
                    activeYear = yearStr.toInt()
                }

                // Extract Sub-type
                if (activeYear != null) {
                    val valType = rowType.getOrNull(c)?.trim() ?: ""
                    if (valType.contains("សំណុំរឿងបានកាត់ក្តី")) {
                        columnMapping[c] = ColMap(activeYear, "adjudicated")
                    } else if (valType.contains("សំណុំរឿង")) {
                        columnMapping[c] = ColMap(activeYear, "received")
                    }
                }
            }

            // 2. Extract Data Rows starting from row index 5
            var currentProCode = ""
            var currentProvName = ""

            for (i in 5 until rows.size) {
                val row = rows[i]
                val c0 = row.getOrNull(0)?.trim() ?: ""
                val c1 = row.getOrNull(1)?.trim() ?: ""
                val caseType = row.getOrNull(2)?.trim() ?: ""
                val itemCode = row.getOrNull(3)?.trim() ?: ""

                // Skip completely blank rows
                if (caseType.isBlank()) continue

                // Update grouping variables if they exist on this line
                if (c0.isNotBlank() && c1.isNotBlank()) {
                    currentProCode = c0
                    currentProvName = c1
                }

                // Group the 2 columns (received, adjudicated) by Year
                val yearData = mutableMapOf<Int, MutableMap<String, Double?>>()
                for ((c, colMap) in columnMapping) {
                    if (!yearData.containsKey(colMap.year)) {
                        yearData[colMap.year] = mutableMapOf("received" to null, "adjudicated" to null)
                    }
                    yearData[colMap.year]!![colMap.type] = KhmerStringUtils.parseKhmerDouble(row.getOrNull(c))
                }

                // Build Records
                for ((year, data) in yearData) {
                    records.add(
                        Nis26A1CaseRecord(
                            proCode = currentProCode,
                            provinceName = currentProvName,
                            caseType = caseType,
                            itemCode = itemCode,
                            year = year,
                            numCases = data["received"],
                            numCasesAdjudicated = data["adjudicated"]
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis26A1CaseRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_26a1_court_cases 
            (pro_code, province_name, case_type, item_code, year, num_cases, num_cases_adjudicated, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (province_name, case_type, year, upload_id) DO UPDATE SET
                pro_code = EXCLUDED.pro_code,
                item_code = EXCLUDED.item_code,
                num_cases = EXCLUDED.num_cases,
                num_cases_adjudicated = EXCLUDED.num_cases_adjudicated
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.proCode)
                stmt.setString(2, r.provinceName)
                stmt.setString(3, r.caseType)
                stmt.setString(4, r.itemCode)
                stmt.setInt(5, r.year)
                stmt.setNullableDouble( 6, r.numCases)
                stmt.setNullableDouble( 7, r.numCasesAdjudicated)
                stmt.setString(8, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    

    

    
}