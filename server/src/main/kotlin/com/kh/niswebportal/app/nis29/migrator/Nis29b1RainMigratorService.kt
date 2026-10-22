package com.kh.niswebportal.app.nis29.migrator

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

data class Nis29b1RainRecord(
    val proCode: String,
    val proName: String,
    val year: Int,
    val month: Int,
    val rainfall: String?
)

@Service
class Nis29b1RainMigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_29b1_rain"

    private companion object {
        private const val BATCH_SIZE = 1000
        private val KHMER_TO_LATIN_MAP = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for nis_29b1_rain import." }

        val records = file.inputStream.use { parseCsv(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                // 1. Remove old records for this source before inserting new ones
//                connection.prepareStatement("DELETE FROM nis_29b1_rain").use { it.executeUpdate() }
//
//                connection.prepareStatement("DELETE FROM datasource_history WHERE source_id = ?").use { stmt ->
//                    stmt.setString(1, sourceId)
//                    stmt.executeUpdate()
//                }

                // 2. Insert the new batch of records
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv(inputStream: InputStream): List<Nis29b1RainRecord> {
        val records = mutableListOf<Nis29b1RainRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 8) return emptyList()

            // Data rows start at index 8 (skipping header at index 7)
            for (i in 8 until rows.size) {
                val row = rows[i]
                val proCode = row.getOrNull(0)?.trim() ?: ""
                val proName = row.getOrNull(1)?.trim() ?: ""
                val yearText = row.getOrNull(2)?.trim()
                val monthText = row.getOrNull(3)?.trim()
                val rainfallText = row.getOrNull(4)

                if (proCode.isBlank() || yearText.isNullOrBlank() || monthText.isNullOrBlank()) continue

                val year = cleanValue(yearText)?.toIntOrNull() ?: continue
                val month = parseMonth(monthText) ?: continue
                val rainfall = cleanValue(rainfallText)

                records.add(
                    Nis29b1RainRecord(
                        proCode = proCode,
                        proName = proName,
                        year = year,
                        month = month,
                        rainfall = rainfall
                    )
                )
            }
        }
        return records
    }

    private fun parseMonth(monthText: String): Int? {
        return when (monthText.lowercase().trim()) {
            "jan", "january", "មករា" -> 1
            "feb", "february", "កុម្ភៈ" -> 2
            "mar", "march", "មីនា" -> 3
            "apr", "april", "មេសា" -> 4
            "may", "ឧសភា" -> 5
            "jun", "june", "មិថុនា" -> 6
            "jul", "july", "កក្កដា" -> 7
            "aug", "august", "សីហា" -> 8
            "sep", "september", "កញ្ញា" -> 9
            "oct", "october", "តុលា" -> 10
            "nov", "november", "វិច្ឆិកា" -> 11
            "dec", "december", "ធ្នូ" -> 12
            else -> monthText.toIntOrNull()
        }
    }

    private fun insertBatch(connection: Connection, records: List<Nis29b1RainRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_29b1_rain
            (pro_code, pro_name, year, month, rainfall, upload_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (pro_code, year, month, upload_id) DO UPDATE SET
                pro_name = EXCLUDED.pro_name,
                rainfall = EXCLUDED.rainfall
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.proCode)
                stmt.setString(2, r.proName)
                stmt.setInt(3, r.year)
                stmt.setInt(4, r.month)
                stmt.setString(5, r.rainfall)
                stmt.setString(6, uploadId)
                stmt.addBatch()

                count++
                if (count % BATCH_SIZE == 0) {
                    stmt.executeBatch()
                }
            }
            if (count % BATCH_SIZE != 0) {
                stmt.executeBatch()
            }
        }
    }

    private fun cleanValue(value: String?): String? {
        val cleaned = value?.trim()?.replace(" ", "")?.replace(",", "") ?: return null
        if (cleaned.isBlank() || cleaned == "-" || cleaned == "nan") return null
        return cleaned.toLatinDigits()
    }

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}