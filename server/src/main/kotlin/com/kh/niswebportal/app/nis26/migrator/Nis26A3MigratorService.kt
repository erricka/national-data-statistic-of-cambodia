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

data class Nis26A3JusticeRecord(
    val blockCode: String,
    val blockName: String,
    val itemCode: String,
    val department: String,
    val description: String,
    val year: Int,
    val countValue: String?
)

@Service
class Nis26A3MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_26a3"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 26a3 import." }

        val records = file.inputStream.use { parseCsv26A3(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv26A3(inputStream: InputStream): List<Nis26A3JusticeRecord> {
        val records = mutableListOf<Nis26A3JusticeRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 4) return emptyList()

            var currentBlockCode = ""
            var currentBlockName = ""
            var currentDepartment = ""
            val yearMappings = mutableListOf<Pair<Int, Int>>() // Pair<Year, ColumnIndex>

            for (i in 2 until rows.size) {
                val row = rows[i]
                val col0 = row.getOrNull(0)?.trim() ?: ""
                val col1 = row.getOrNull(1)?.trim() ?: ""
                val col2 = row.getOrNull(2)?.trim() ?: ""

                // 1. Detect Block Header (e.g., 26A3_1, 26A3_2, 26A3_3)
                if (col0.matches(Regex("^26A3_[1-9]$"))) {
                    currentBlockCode = col0
                    currentBlockName = col1
                    continue
                }

                // 2. Detect Year Mappings for Blocks 1 & 2
                if (col1 == "ឆ្នាំ" || col1 == "ទិន្នន័យ") {
                    yearMappings.clear()
                    for (c in 2 until row.size()) {
                        val valStr = row.getOrNull(c)?.trim()?.replace(".0", "") ?: ""
                        if (valStr.matches(Regex("^[0-9]{4}$"))) yearMappings.add(Pair(valStr.toInt(), c))
                    }
                    continue
                }

                // 3. Detect Year Mappings for Block 3
                if (col2 == "ឆ្នាំ") {
                    yearMappings.clear()
                    for (c in 3 until row.size()) {
                        val valStr = row.getOrNull(c)?.trim()?.replace(".0", "") ?: ""
                        if (valStr.matches(Regex("^[0-9]{4}$"))) yearMappings.add(Pair(valStr.toInt(), c))
                    }
                    continue
                }

                // 4. Extract Data Rows
                if (col0.isNotEmpty() && col0.contains("_")) {
                    if (currentBlockCode == "26A3_3") {
                        if (col1.isNotEmpty()) {
                            currentDepartment = col1
                        }
                        val description = col2
                        for ((year, colIdx) in yearMappings) {
                            val value = row.getOrNull(colIdx)?.trim().takeIf { !it.isNullOrBlank() }
                            records.add(
                                Nis26A3JusticeRecord(
                                    blockCode = currentBlockCode,
                                    blockName = currentBlockName,
                                    itemCode = col0,
                                    department = currentDepartment,
                                    description = description,
                                    year = year,
                                    countValue = value
                                )
                            )
                        }
                    } else {
                        val description = col1
                        for ((year, colIdx) in yearMappings) {
                            val value = row.getOrNull(colIdx)?.trim().takeIf { !it.isNullOrBlank() }
                            records.add(
                                Nis26A3JusticeRecord(
                                    blockCode = currentBlockCode,
                                    blockName = currentBlockName,
                                    itemCode = col0,
                                    department = "",
                                    description = description,
                                    year = year,
                                    countValue = value
                                )
                            )
                        }
                    }
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis26A3JusticeRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_26a3_justice_stats 
            (block_code, block_name, item_code, department, description, year, count_value, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (item_code, year, upload_id) DO UPDATE SET
                block_code = EXCLUDED.block_code,
                block_name = EXCLUDED.block_name,
                department = EXCLUDED.department,
                description = EXCLUDED.description,
                count_value = EXCLUDED.count_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.blockCode)
                stmt.setString(2, r.blockName)
                stmt.setString(3, r.itemCode)
                stmt.setString(4, r.department)
                stmt.setString(5, r.description)
                stmt.setInt(6, r.year)
                stmt.setString(7, r.countValue)
                stmt.setString(8, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    
}