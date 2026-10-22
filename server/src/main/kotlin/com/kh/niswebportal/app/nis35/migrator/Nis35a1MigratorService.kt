package com.kh.niswebportal.app.nis35.migrator

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
import java.util.ArrayList
import javax.sql.DataSource

data class Nis35A1Record(
    val itemCode: String,
    val itemName: String,
    val year: Int,
    val quarter: Int,
    val indexValue: String?
)

private data class PeriodColumnMapping(
    val year: Int,
    val quarter: Int,
    val colIndex: Int
)

@Service
class Nis35A1MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_35a1"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 35a1 import." }

        val records = file.inputStream.use { stream -> parseCsv35A1(stream) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { conn -> insertBatch(conn, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv35A1(inputStream: InputStream): List<Nis35A1Record> {
        val records = ArrayList<Nis35A1Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 4) return emptyList()

            val headerRow = rows[4]
            val periodMappings = detectPeriodMappings(headerRow)

            for (i in 5 until rows.size) {
                val row = rows[i]
                if (row.size() < 2) continue

                val itemCode = row.getOrNull(0)?.trim()
                val itemName = row.getOrNull(1)?.trim()

                if (itemCode.isNullOrBlank() || itemName.isNullOrBlank()) continue

                for (mapping in periodMappings) {
                    val valueText = row.getOrNull(mapping.colIndex)?.trim()

                    if (!valueText.isNullOrBlank()) {
                        records.add(
                            Nis35A1Record(
                                itemCode = itemCode,
                                itemName = itemName,
                                year = mapping.year,
                                quarter = mapping.quarter,
                                indexValue = valueText
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun detectPeriodMappings(headerRow: CSVRecord): List<PeriodColumnMapping> {
        val mappings = ArrayList<PeriodColumnMapping>()
        val lastCol = headerRow.size()

        val qRegex = Regex("""^(\d{2})Q([1-4])$""", RegexOption.IGNORE_CASE)
        val yearRegex = Regex("""^(\d{4})$""")

        for (colIndex in 2 until lastCol) {
            val text = headerRow.getOrNull(colIndex)?.trim() ?: continue

            val qMatch = qRegex.find(text)
            if (qMatch != null) {
                val shortYear = qMatch.groupValues[1].toInt()
                val quarter = qMatch.groupValues[2].toInt()
                val fullYear = 2000 + shortYear
                mappings.add(PeriodColumnMapping(fullYear, quarter, colIndex))
                continue
            }

            val yearMatch = yearRegex.find(text)
            if (yearMatch != null) {
                val fullYear = yearMatch.groupValues[1].toInt()
                mappings.add(PeriodColumnMapping(fullYear, 0, colIndex))
            }
        }
        return mappings
    }

    private fun insertBatch(connection: Connection, records: List<Nis35A1Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_35a1_balance_of_payments 
            (item_code, item_name, year, quarter, index_value, upload_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (item_code, year, quarter, upload_id) DO UPDATE SET
                item_name = EXCLUDED.item_name,
                index_value = EXCLUDED.index_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.itemCode)
                stmt.setString(2, r.itemName)
                stmt.setInt(3, r.year)
                stmt.setInt(4, r.quarter)
                stmt.setString(5, r.indexValue)
                stmt.setString(6, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    
}