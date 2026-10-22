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

data class Nis26A2OfficialRecord(
    val proCode: String,
    val provinceName: String,
    val year: Int,
    val totalOfficials: Double?,
    val adminOfficials: Double?,
    val clerks: Double?,
    val bailiffs: Double?
)

@Service
class Nis26A2MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_26a2"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 26a2 import." }

        val records = file.inputStream.use { parseCsv26A2(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv26A2(inputStream: InputStream): List<Nis26A2OfficialRecord> {
        val records = mutableListOf<Nis26A2OfficialRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 5) return emptyList()

            // 1. Locate Header Rows (Rows 2 and 3 in index) to map columns to Years & Types
            val rowYear = rows[2]
            val rowType = rows[3]

            data class ColMap(val year: Int, val type: String)
            val columnMapping = mutableMapOf<Int, ColMap>() // colIndex -> ColMap

            var activeYear: Int? = null
            for (c in 2 until rowYear.size()) {
                val valYear = rowYear.getOrNull(c)?.trim() ?: ""

                // Extract Year from merged cell header
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
                    if (valType.contains("សរុប")) {
                        columnMapping[c] = ColMap(activeYear, "total")
                    } else if (valType.contains("មន្រ្តីរដ្ឋបាល")) {
                        columnMapping[c] = ColMap(activeYear, "admin")
                    } else if (valType.contains("ក្រឡាបញ្ជី")) {
                        columnMapping[c] = ColMap(activeYear, "clerk")
                    } else if (valType.contains("អាជ្ញាសាលា")) {
                        columnMapping[c] = ColMap(activeYear, "bailiff")
                    }
                }
            }

            // 2. Extract Data Rows starting from row index 5
            for (i in 5 until rows.size) {
                val row = rows[i]
                val proCode = row.getOrNull(0)?.trim() ?: ""
                val provinceName = row.getOrNull(1)?.trim() ?: ""

                // Skip completely blank rows or summary rows
                if (provinceName.isBlank() || provinceName == "Provinces_name") continue

                // Group the 4 columns (Total, Admin, Clerk, Bailiff) by Year
                val yearData = mutableMapOf<Int, MutableMap<String, Double?>>()
                for ((c, colMap) in columnMapping) {
                    if (!yearData.containsKey(colMap.year)) {
                        yearData[colMap.year] = mutableMapOf("total" to null, "admin" to null, "clerk" to null, "bailiff" to null)
                    }
                    yearData[colMap.year]!![colMap.type] = KhmerStringUtils.parseKhmerDouble(row.getOrNull(c))
                }

                // Build Records
                for ((year, data) in yearData) {
                    records.add(
                        Nis26A2OfficialRecord(
                            proCode = proCode,
                            provinceName = provinceName,
                            year = year,
                            totalOfficials = data["total"],
                            adminOfficials = data["admin"],
                            clerks = data["clerk"],
                            bailiffs = data["bailiff"]
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis26A2OfficialRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_26a2_court_officials 
            (pro_code, province_name, year, total_officials, admin_officials, clerks, bailiffs, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (province_name, year, upload_id) DO UPDATE SET
                pro_code = EXCLUDED.pro_code,
                total_officials = EXCLUDED.total_officials,
                admin_officials = EXCLUDED.admin_officials,
                clerks = EXCLUDED.clerks,
                bailiffs = EXCLUDED.bailiffs
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.proCode)
                stmt.setString(2, r.provinceName)
                stmt.setInt(3, r.year)
                stmt.setNullableDouble( 4, r.totalOfficials)
                stmt.setNullableDouble( 5, r.adminOfficials)
                stmt.setNullableDouble( 6, r.clerks)
                stmt.setNullableDouble( 7, r.bailiffs)
                stmt.setString(8, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    

    

    
}