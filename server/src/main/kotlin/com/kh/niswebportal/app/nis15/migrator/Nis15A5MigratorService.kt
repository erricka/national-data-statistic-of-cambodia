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

data class Nis15A5ExportRecord(
    val serialNo: String,
    val coForm: String,
    val categoryDesc: String,
    val year: Int,
    val countValue: Double?
)

@Service
class Nis15A5MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_15a5"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 15a5 import." }

        val records = file.inputStream.use { parseCsv15A5(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv15A5(inputStream: InputStream): List<Nis15A5ExportRecord> {
        val records = mutableListOf<Nis15A5ExportRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 4) return emptyList()

            // 1. Locate the Header Row (Row index 2) to dynamically map columns to Years
            val yearRow = rows[2]
            val yearMappings = mutableListOf<Pair<Int, Int>>() // Pair<Year, ColumnIndex>

            // Yearly data starts at column index 3
            for (c in 3 until yearRow.size()) {
                val valStr = yearRow.getOrNull(c)?.trim()?.replace(".0", "") ?: ""
                if (valStr.matches(Regex("^[0-9]{4}$"))) {
                    yearMappings.add(Pair(valStr.toInt(), c))
                }
            }

            // 2. Extract Data starting directly after the header row (index 3)
            for (i in 3 until rows.size) {
                val row = rows[i]
                val serialNo = row.getOrNull(0)?.trim() ?: ""
                val coForm = row.getOrNull(1)?.trim() ?: ""
                val categoryDesc = row.getOrNull(2)?.trim() ?: ""

                // Stop processing if it's an empty line
                if (coForm.isBlank() && categoryDesc.isBlank()) continue

                for ((year, colIndex) in yearMappings) {
                    records.add(
                        Nis15A5ExportRecord(
                            serialNo = serialNo,
                            coForm = coForm,
                            categoryDesc = categoryDesc,
                            year = year,
                            countValue = KhmerStringUtils.parseKhmerDouble(row.getOrNull(colIndex))
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis15A5ExportRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_15a5_export_preferences 
            (serial_no, co_form, category_desc, year, count_value, upload_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (co_form, year, upload_id) DO UPDATE SET
                serial_no = EXCLUDED.serial_no,
                category_desc = EXCLUDED.category_desc,
                count_value = EXCLUDED.count_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.serialNo)
                stmt.setString(2, r.coForm)
                stmt.setString(3, r.categoryDesc)
                stmt.setInt(4, r.year)
                stmt.setNullableDouble( 5, r.countValue)
                stmt.setString(6, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    

    

    
}