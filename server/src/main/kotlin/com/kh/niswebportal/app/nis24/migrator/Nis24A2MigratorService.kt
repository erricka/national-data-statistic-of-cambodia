package com.kh.niswebportal.app.nis24.migrator

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

data class Nis24A2Record(
    val itemCode: String,
    val description: String,
    val year: Int,
    val countValue: Double?
)

@Service
class Nis24A2MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_24a2"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 24A2 import." }

        val records = file.inputStream.use { parseCsv24A2(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv24A2(inputStream: InputStream): List<Nis24A2Record> {
        val records = mutableListOf<Nis24A2Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 6) return emptyList()

            // 1. Locate Header Rows (Rows 3 and 4 in index) to dynamically map columns
            val rowDesc = rows[3]
            val rowCode = rows[4]

            data class ColMap(val code: String, val desc: String)
            val columnMapping = mutableMapOf<Int, ColMap>() // colIndex -> ColMap

            for (c in 1 until rowCode.size()) {
                val code = rowCode.getOrNull(c)?.trim() ?: ""
                val desc = rowDesc.getOrNull(c)?.trim() ?: ""

                // Only map valid configuration columns containing system codes
                if (code.isNotBlank() && code != "nan" && code.contains("24A2_")) {
                    columnMapping[c] = ColMap(code, desc)
                }
            }

            // 2. Extract Data Rows starting from row index 5 (where the Years start)
            for (i in 5 until rows.size) {
                val row = rows[i]

                // Column 0 is always the Year
                val yearStr = row.getOrNull(0)?.trim() ?: ""
                val year = yearStr.toIntOrNull()

                if (year != null) {
                    for ((c, colMap) in columnMapping) {
                        records.add(
                            Nis24A2Record(
                                itemCode = colMap.code,
                                description = colMap.desc,
                                year = year,
                                countValue = KhmerStringUtils.parseKhmerDouble(row.getOrNull(c))
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis24A2Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_24a2_eco_health_edu_stats 
            (item_code, description, year, count_value, upload_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (item_code, year, upload_id) DO UPDATE SET
                description = EXCLUDED.description,
                count_value = EXCLUDED.count_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.itemCode)
                stmt.setString(2, r.description)
                stmt.setInt(3, r.year)
                stmt.setNullableDouble( 4, r.countValue)
                stmt.setString(5, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    

    

    
}