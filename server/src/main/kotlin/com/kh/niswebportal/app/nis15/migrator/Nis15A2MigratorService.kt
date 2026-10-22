package com.kh.niswebportal.app.nis15.migrator

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

data class Nis15A2BranchRecord(
    val serialNo: String,
    val registrationType: String,
    val year: Int,
    val countValue: Double?
)

@Service
class Nis15A2MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_15a2"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 15a2 import." }

        val records = file.inputStream.use { parseCsv15A2(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv15A2(inputStream: InputStream): List<Nis15A2BranchRecord> {
        val records = mutableListOf<Nis15A2BranchRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 4) return emptyList()

            // 1. Locate the Header Row (Row 2 in Excel)
            var headerRowIndex = -1
            for (i in 0 until rows.size) {
                if (rows[i].getOrNull(1)?.trim() == "ល.រ") {
                    headerRowIndex = i
                    break
                }
            }
            if (headerRowIndex == -1) return emptyList()

            val yearRow = rows[headerRowIndex]
            val yearMappings = mutableListOf<Pair<Int, Int>>() // Pair<Year, ColumnIndex>

            // 2. Yearly data starts at column index 3
            for (c in 3 until yearRow.size()) {
                val valStr = yearRow.getOrNull(c)?.trim()?.replace(".0", "") ?: ""
                if (valStr.matches(Regex("^[0-9]{4}$"))) {
                    yearMappings.add(Pair(valStr.toInt(), c))
                }
            }

            // 3. Extract Data starting directly after the header row
            for (i in (headerRowIndex + 1) until rows.size) {
                val row = rows[i]
                val serialNo = row.getOrNull(1)?.trim() ?: ""
                val registrationType = row.getOrNull(2)?.trim() ?: ""

                if (registrationType.isBlank()) continue

                for ((year, colIndex) in yearMappings) {
                    records.add(
                        Nis15A2BranchRecord(
                            serialNo = serialNo,
                            registrationType = registrationType,
                            year = year,
                            countValue = KhmerStringUtils.parseKhmerDouble(row.getOrNull(colIndex))
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis15A2BranchRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_15a2_branch_registration 
            (serial_no, registration_type, year, count_value, upload_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (registration_type, year, upload_id) DO UPDATE SET
                serial_no = EXCLUDED.serial_no,
                count_value = EXCLUDED.count_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.serialNo)
                stmt.setString(2, r.registrationType)
                stmt.setInt(3, r.year)
                stmt.setNullableDouble( 4, r.countValue)
                stmt.setString(5, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    

    

    
}