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

data class Nis26A6Record(
    val blockCode: String,
    val blockName: String,
    val itemCode: String,
    val description: String,
    val year: Int,
    val countValue: String?
)

@Service
class Nis26A6MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_26a6"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 26a6 import." }

        val records = file.inputStream.use { parseCsv26A6(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv26A6(inputStream: InputStream): List<Nis26A6Record> {
        val records = mutableListOf<Nis26A6Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            var currentBlockCode = ""
            var currentBlockName = ""

            // Block 1 state
            val block1Years = mutableMapOf<Int, Int>() // colIndex -> Year

            // Block 2 state
            val block2Years = mutableMapOf<Int, Int>() // colIndex -> Year (maps merged cells)
            var block2CodesRow: CSVRecord? = null

            for (i in 0 until rows.size) {
                val row = rows[i]
                val col0 = row.getOrNull(0)?.trim() ?: ""
                val col1 = row.getOrNull(1)?.trim() ?: ""

                // 1. Detect Main Blocks
                if (col0.matches(Regex("^26A6_[1-9]$"))) {
                    currentBlockCode = col0
                    currentBlockName = col1
                    continue
                }

                // 2. Process Block 1 (Vertical layout)
                if (currentBlockCode == "26A6_1") {
                    if (col1 == "ឆ្នាំ") {
                        block1Years.clear()
                        for (c in 2 until row.size()) {
                            val valStr = row.getOrNull(c)?.trim()?.replace(".0", "") ?: ""
                            if (valStr.matches(Regex("^[0-9]{4}$"))) {
                                block1Years[c] = valStr.toInt()
                            }
                        }
                    } else if (col0.startsWith("26A6_1_")) {
                        for ((c, year) in block1Years) {
                            val value = row.getOrNull(c)?.trim().takeIf { !it.isNullOrBlank() }
                            records.add(Nis26A6Record("26A6_1", currentBlockName, col0, col1, year, value))
                        }
                    }
                }

                // 3. Process Block 2 (Horizontal merged layout)
                else if (currentBlockCode == "26A6_2") {

                    // A. Extract continuous year mappings from merged header cells
                    if (block2Years.isEmpty()) {
                        var hasYear = false
                        var currentYear: Int? = null
                        for (c in 1 until row.size()) {
                            val valStr = row.getOrNull(c)?.trim() ?: ""
                            val match = Regex("(20\\d\\d)").find(valStr)
                            if (match != null) {
                                currentYear = match.groupValues[1].toInt()
                                hasYear = true
                            }
                            if (currentYear != null) block2Years[c] = currentYear
                        }
                        if (!hasYear) block2Years.clear() // Reset if it was a false positive line
                    }

                    // B. Pinpoint the specific row containing the system codes
                    else if (col1.contains("26A6_2_1_T")) {
                        block2CodesRow = row
                    }

                    // C. Extract the values row directly beneath the codes row
                    else if (block2CodesRow != null && block2Years.isNotEmpty()) {
                        for ((c, year) in block2Years) {
                            val code = block2CodesRow!!.getOrNull(c)?.trim() ?: ""
                            val value = row.getOrNull(c)?.trim().takeIf { !it.isNullOrBlank() }

                            // Map codes to clean, unified descriptions (ignoring messy Excel text)
                            val desc = when {
                                code.endsWith("_T") -> "សរុប ករណី"
                                code.endsWith("_Succs") -> "ជោគជ័យ"
                                code.endsWith("_Finalise") -> "បិទបញ្ចប់"
                                code.endsWith("_Cancel") -> "លើកចោល"
                                else -> code
                            }

                            if (code.isNotBlank() && code != "nan") {
                                records.add(Nis26A6Record("26A6_2", currentBlockName, code, desc, year, value))
                            }
                        }
                        block2CodesRow = null // Processed, clear to prevent re-triggering
                    }
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis26A6Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_26a6_justice_records 
            (block_code, block_name, item_code, description, year, count_value, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (item_code, year, upload_id) DO UPDATE SET
                block_code = EXCLUDED.block_code,
                block_name = EXCLUDED.block_name,
                description = EXCLUDED.description,
                count_value = EXCLUDED.count_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.blockCode)
                stmt.setString(2, r.blockName)
                stmt.setString(3, r.itemCode)
                stmt.setString(4, r.description)
                stmt.setInt(5, r.year)
                stmt.setString(6, r.countValue)
                stmt.setString(7, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    
}