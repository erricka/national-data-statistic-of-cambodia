package com.kh.niswebportal.app.nis18.migrator

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

data class Nis18a9Record(
    val artObjectType: String,
    val year: String,
    val quantity: String?
)

@Service
class Nis18a9MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_18a9"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 18 A9 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv18a9(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv18a9(inputStream: InputStream): List<Nis18a9Record> {
        val records = mutableListOf<Nis18a9Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 8) return emptyList()

            // Header at row index 8 contains year columns (e.g. ឆ្នាំ២០២៤, ឆ្នាំ២០២៥)
            val rowHeader = rows[8]
            val yearColMap = mutableMapOf<Int, String>()

            for (c in 2 until rowHeader.size()) {
                val headerStr = rowHeader.getOrNull(c)?.trim() ?: ""
                val yearMatch = Regex("\\d{4}").find(headerStr.toLatinDigits())?.value
                if (yearMatch != null) {
                    yearColMap[c] = yearMatch
                }
            }

            if (yearColMap.isEmpty()) {
                yearColMap[2] = "2024"
            }

            // Data rows start from row index 10 onwards
            for (i in 10 until rows.size) {
                val row = rows[i]
                val artObjectType = row.getOrNull(1)?.trim() ?: ""

                if (artObjectType.isBlank() || artObjectType == "nan" || artObjectType == "សរុប") {
                    continue
                }

                for ((c, year) in yearColMap) {
                    val rawVal = row.getOrNull(c)

                    records.add(
                        Nis18a9Record(
                            artObjectType = artObjectType,
                            year = year,
                            quantity = cleanValue(rawVal)
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis18a9Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_18a9_art_objects_stats
            (upload_id, art_object_type, year, quantity)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (art_object_type, year, upload_id) DO UPDATE SET
                quantity = EXCLUDED.quantity
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, uploadId)
                stmt.setString(2, r.artObjectType)
                stmt.setString(3, r.year)
                stmt.setString(4, r.quantity)
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