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

data class Nis15A1RegistrationRecord(
    val categoryName: String,
    val itemCode: String,
    val serialNo: String,
    val description: String,
    val year: Int,
    val countValue: Double?
)

@Service
class Nis15A1MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_15a1"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 15a1 import." }

        val records = file.inputStream.use { parseCsv15A1(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv15A1(inputStream: InputStream): List<Nis15A1RegistrationRecord> {
        val records = mutableListOf<Nis15A1RegistrationRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 4) return emptyList()

            // 1. Map Columns to Years from Row 2 (Index 2)
            val yearMappings = mutableListOf<Pair<Int, Int>>() // Pair<Year, ColumnIndex>
            val yearRow = rows[2]

            // Yearly data starts at column index 4
            for (c in 4 until yearRow.size()) {
                val valStr = yearRow.getOrNull(c)?.trim()?.replace(".0", "") ?: ""
                if (valStr.matches(Regex("^[0-9]{4}$"))) {
                    yearMappings.add(Pair(valStr.toInt(), c))
                }
            }

            var currentCategory = ""

            // 2. Extract Data starting at Row 4
            for (i in 4 until rows.size) {
                val row = rows[i]
                val col0 = row.getOrNull(0)?.trim() ?: ""
                val col1 = row.getOrNull(1)?.trim() ?: ""
                val col2 = row.getOrNull(2)?.trim() ?: ""
                val col3 = row.getOrNull(3)?.trim() ?: ""

                // Ignore explicit "Total" (សរុប) rows since they can be derived, or empty rows
                if (col0.isBlank() && col1.contains("សរុប")) continue
                if (col0.isBlank() && col1.isBlank()) continue

                // Detect Category Grouping Row (Has Item Code in Col0, Text in Col1, but Col2/Col3 are blank)
                if (col0.isNotEmpty() && col1.isNotEmpty() && col2.isBlank() && col3.isBlank()) {
                    currentCategory = col1
                    continue
                }

                // Detect Data Row
                if (col2.isNotEmpty() && col3.isNotEmpty()) {
                    for ((year, colIndex) in yearMappings) {
                        records.add(
                            Nis15A1RegistrationRecord(
                                categoryName = currentCategory,
                                itemCode = col2, // We use the sub-level code as the unique identifier
                                serialNo = col1,
                                description = col3,
                                year = year,
                                countValue = KhmerStringUtils.parseKhmerDouble(row.getOrNull(colIndex))
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis15A1RegistrationRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_15a1_business_registration 
            (category_name, item_code, serial_no, description, year, count_value, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (item_code, year, upload_id) DO UPDATE SET
                category_name = EXCLUDED.category_name,
                serial_no = EXCLUDED.serial_no,
                description = EXCLUDED.description,
                count_value = EXCLUDED.count_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.categoryName)
                stmt.setString(2, r.itemCode)
                stmt.setString(3, r.serialNo)
                stmt.setString(4, r.description)
                stmt.setInt(5, r.year)
                stmt.setNullableDouble( 6, r.countValue)
                stmt.setString(7, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    

    

    
}