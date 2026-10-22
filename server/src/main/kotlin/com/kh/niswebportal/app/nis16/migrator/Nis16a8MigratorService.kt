package com.kh.niswebportal.app.nis16.migrator

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

data class Nis16a8Record(
    val proCode: String,
    val proName: String,
    val year: Int,
    val classCount: Int
)

@Service
class Nis16a8MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_16a8"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for nis_16a8 import." }

        val records = file.inputStream.use { parseCsv(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                // 1. Clear previous records and history for override behavior
//                connection.prepareStatement("DELETE FROM nis_16a8").use { it.executeUpdate() }
//
//                connection.prepareStatement("DELETE FROM datasource_history WHERE source_id = ?").use { stmt ->
//                    stmt.setString(1, sourceId)
//                    stmt.executeUpdate()
//                }

                // 2. Insert new batch of records with ON CONFLICT update support
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv(inputStream: InputStream): List<Nis16a8Record> {
        val records = mutableListOf<Nis16a8Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 8) return emptyList()

            // Row index 8 contains the years header starting from column index 2
            val headerRow = rows[8]
            val yearColumns = mutableMapOf<Int, Int>() // colIndex -> Year

            for (colIdx in 2 until headerRow.size()) {
                val yearText = headerRow.getOrNull(colIdx)?.trim() ?: continue
                val year = cleanValue(yearText)?.substringBefore(".")?.toIntOrNull()
                if (year != null) {
                    yearColumns[colIdx] = year
                }
            }

            // Data rows start from index 9 onwards
            for (i in 9 until rows.size) {
                val row = rows[i]
                val proCode = row.getOrNull(0)?.trim() ?: ""
                val proName = row.getOrNull(1)?.trim() ?: ""

                if (proCode.isBlank() || proCode.lowercase() == "nan") continue

                for ((colIdx, year) in yearColumns) {
                    val countVal = cleanValue(row.getOrNull(colIdx))

                    records.add(
                        Nis16a8Record(
                            proCode = proCode,
                            proName = proName,
                            year = year,
                            classCount = parseKhmerInt(countVal)
                        )
                    )
                }
            }
        }
        return records
    }

    
    private fun parseKhmerInt(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        val trimmed = text.trim()
        if (trimmed == "-" || trimmed.contains("-")) return 0
        
        val normalized = trimmed.replace("១", "1").replace("២", "2").replace("៣", "3")
            .replace("៤", "4").replace("៥", "5").replace("៦", "6")
            .replace("៧", "7").replace("៨", "8").replace("៩", "9").replace("០", "0")
            
        val cleanNumber = normalized.replace(",", "").replace(" ", "")
        val withoutDecimal = if (cleanNumber.endsWith(".0")) cleanNumber.substring(0, cleanNumber.length - 2) else cleanNumber
        return withoutDecimal.toDoubleOrNull()?.toInt() ?: 0
    }

    private fun insertBatch(connection: Connection, records: List<Nis16a8Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_16a8
            (pro_code, pro_name, year, class_count, upload_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (pro_code, year, upload_id) DO UPDATE SET
                pro_name = EXCLUDED.pro_name,
                class_count = EXCLUDED.class_count
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.proCode)
                stmt.setString(2, r.proName)
                stmt.setInt(3, r.year)
                stmt.setInt(4, r.classCount)
                stmt.setString(5, uploadId)
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
        if (cleaned.isBlank() || cleaned == "-" || cleaned.lowercase() == "n.a" || cleaned.lowercase() == "nan") return null
        return cleaned.toLatinDigits()
    }

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}