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

data class Nis26A9VerdictRecord(
    val proCode: String,
    val provinceName: String,
    val caseType: String,
    val itemCode: String,
    val year: Int,
    val numCaseVerdicts: Double?,
    val numCaseAdjudicated: Double?
)

@Service
class Nis26A9MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_26a9"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 26a9 import." }

        val records = file.inputStream.use { parseCsv26A9(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv26A9(inputStream: InputStream): List<Nis26A9VerdictRecord> {
        val records = mutableListOf<Nis26A9VerdictRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 6) return emptyList()

            // 1. Locate Header Rows (Rows 3 and 4 in Excel/CSV) to map columns to Years & Types
            val rowYear = rows[3]
            val rowType = rows[4]

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
                    if (valType.contains("សាលក្រម")) {
                        columnMapping[c] = ColMap(activeYear, "verdict")
                    } else if (valType.contains("សាលដីការ")) {
                        columnMapping[c] = ColMap(activeYear, "adjudication")
                    }
                }
            }

            // 2. Extract Data Rows starting from row 6
            var currentProCode = ""
            var currentProvName = ""

            for (i in 6 until rows.size) {
                val row = rows[i]
                val c0 = row.getOrNull(0)?.trim() ?: ""
                val c1 = row.getOrNull(1)?.trim() ?: ""
                val caseType = row.getOrNull(2)?.trim() ?: ""
                val itemCode = row.getOrNull(3)?.trim() ?: ""

                // Blank rows
                if (caseType.isBlank()) continue

                // Update grouping variables if they exist on this line
                if (c0.isNotBlank() && c1.isNotBlank()) {
                    currentProCode = c0
                    currentProvName = c1
                }

                // Group the 2 columns (verdicts, adjudications) by Year
                val yearData = mutableMapOf<Int, MutableMap<String, Double?>>()
                for ((c, colMap) in columnMapping) {
                    if (!yearData.containsKey(colMap.year)) {
                        yearData[colMap.year] = mutableMapOf("verdict" to null, "adjudication" to null)
                    }
                    yearData[colMap.year]!![colMap.type] = KhmerStringUtils.parseKhmerDouble(row.getOrNull(c))
                }

                // Build Records
                for ((year, data) in yearData) {
                    records.add(
                        Nis26A9VerdictRecord(
                            proCode = currentProCode,
                            provinceName = currentProvName,
                            caseType = caseType,
                            itemCode = itemCode,
                            year = year,
                            numCaseVerdicts = data["verdict"],
                            numCaseAdjudicated = data["adjudication"]
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis26A9VerdictRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_26a9_court_verdicts 
            (pro_code, province_name, case_type, item_code, year, num_case_verdicts, num_case_adjudicated, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (province_name, case_type, year, upload_id) DO UPDATE SET
                pro_code = EXCLUDED.pro_code,
                item_code = EXCLUDED.item_code,
                num_case_verdicts = EXCLUDED.num_case_verdicts,
                num_case_adjudicated = EXCLUDED.num_case_adjudicated
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.proCode)
                stmt.setString(2, r.provinceName)
                stmt.setString(3, r.caseType)
                stmt.setString(4, r.itemCode)
                stmt.setInt(5, r.year)
                stmt.setNullableDouble( 6, r.numCaseVerdicts)
                stmt.setNullableDouble( 7, r.numCaseAdjudicated)
                stmt.setString(8, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    

    

    
}