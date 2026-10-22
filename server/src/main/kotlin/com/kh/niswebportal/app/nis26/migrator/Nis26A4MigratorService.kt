package com.kh.niswebportal.app.nis26.migrator

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

data class Nis26A4DisseminationRecord(
    val itemCode: String,
    val description: String,
    val unit: String,
    val year: Int,
    val countValue: String?
)

@Service
class Nis26A4MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_26a4"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 26a4 import." }

        val records = file.inputStream.use { parseCsv26A4(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv26A4(inputStream: InputStream): List<Nis26A4DisseminationRecord> {
        val records = mutableListOf<Nis26A4DisseminationRecord>()

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
                val itemCode = row.getOrNull(0)?.trim() ?: ""
                val description = row.getOrNull(1)?.trim() ?: ""
                val unit = row.getOrNull(2)?.trim() ?: ""

                // Stop processing if description is completely empty
                if (description.isBlank()) continue

                for ((year, colIndex) in yearMappings) {
                    val countValue = row.getOrNull(colIndex)?.trim()

                    records.add(
                        Nis26A4DisseminationRecord(
                            itemCode = itemCode,
                            description = description,
                            unit = unit,
                            year = year,
                            countValue = countValue
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis26A4DisseminationRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_26a4_law_dissemination 
            (item_code, description, unit, year, count_value, upload_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (description, year, upload_id) DO UPDATE SET
                item_code = EXCLUDED.item_code,
                unit = EXCLUDED.unit,
                count_value = EXCLUDED.count_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.itemCode)
                stmt.setString(2, r.description)
                stmt.setString(3, r.unit)
                stmt.setInt(4, r.year)
                stmt.setString(5, r.countValue)
                stmt.setString(6, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    
}