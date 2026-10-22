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

data class Nis15A4CoRecord(
    val itemCode: String,
    val serialNo: String,
    val coForm: String,
    val agreementScheme: String,
    val year: Int,
    val countValue: Double?
)

@Service
class Nis15A4MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_15a4"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 15a4 import." }

        val records = file.inputStream.use { parseCsv15A4(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv15A4(inputStream: InputStream): List<Nis15A4CoRecord> {
        val records = mutableListOf<Nis15A4CoRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 4) return emptyList()

            // 1. Locate the Header Row (Row index 2) to dynamically map columns to Years
            val yearRow = rows[2]
            val yearMappings = mutableListOf<Pair<Int, Int>>() // Pair<Year, ColumnIndex>

            // Yearly data starts at column index 4
            for (c in 4 until yearRow.size()) {
                val valStr = yearRow.getOrNull(c)?.trim()?.replace(".0", "") ?: ""
                if (valStr.matches(Regex("^[0-9]{4}$"))) {
                    yearMappings.add(Pair(valStr.toInt(), c))
                }
            }

            // 2. Extract Data starting directly after the header row (index 3)
            for (i in 3 until rows.size) {
                val row = rows[i]
                val itemCode = row.getOrNull(0)?.trim() ?: ""
                val serialNo = row.getOrNull(1)?.trim() ?: ""
                val coForm = row.getOrNull(2)?.trim() ?: ""
                val agreementScheme = row.getOrNull(3)?.trim() ?: ""

                // Stop processing if it's an empty line or a summary line without codes
                if (itemCode.isBlank() && agreementScheme.isBlank()) continue

                for ((year, colIndex) in yearMappings) {
                    records.add(
                        Nis15A4CoRecord(
                            itemCode = itemCode,
                            serialNo = serialNo,
                            coForm = coForm,
                            agreementScheme = agreementScheme,
                            year = year,
                            countValue = KhmerStringUtils.parseKhmerDouble(row.getOrNull(colIndex))
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis15A4CoRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_15a4_certificate_of_origin 
            (item_code, serial_no, co_form, agreement_scheme, year, count_value, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (item_code, year, upload_id) DO UPDATE SET
                serial_no = EXCLUDED.serial_no,
                co_form = EXCLUDED.co_form,
                agreement_scheme = EXCLUDED.agreement_scheme,
                count_value = EXCLUDED.count_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.itemCode)
                stmt.setString(2, r.serialNo)
                stmt.setString(3, r.coForm)
                stmt.setString(4, r.agreementScheme)
                stmt.setInt(5, r.year)
                stmt.setNullableDouble( 6, r.countValue)
                stmt.setString(7, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    

    

    
}