package com.kh.niswebportal.app.nis22.migrator

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
import java.sql.PreparedStatement
import java.sql.Types
import javax.sql.DataSource

data class Nis22A4Record(
    val year: Int,
    val internetSubscribers: Long?
)

@Service
class Nis22A4MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_22a4"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 22a4 import." }

        val records = file.inputStream.use { parseCsv22A4(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv22A4(inputStream: InputStream): List<Nis22A4Record> {
        val records = mutableListOf<Nis22A4Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.isEmpty()) return emptyList()

            // 1. Locate technical header row containing '22a4_year'
            var headerRowIndex = -1
            for (i in rows.indices) {
                if (rows[i].getOrNull(1)?.trim() == "22a4_year") {
                    headerRowIndex = i
                    break
                }
            }

            val startIndex = if (headerRowIndex != -1) headerRowIndex + 1 else 4

            // 2. Parse yearly internet subscriber records
            for (i in startIndex until rows.size) {
                val row = rows[i]
                if (row.size() < 2) continue

                val yearText = row.getOrNull(1)?.trim()
                if (yearText.isNullOrBlank() || yearText.contains("Source:", ignoreCase = true)) continue

                val year = parseKhmerLong(yearText)?.toInt() ?: continue
                if (year !in 1900..2099) continue

                val internetSubscribers = parseKhmerLong(row.getOrNull(2))

                records.add(
                    Nis22A4Record(
                        year = year,
                        internetSubscribers = internetSubscribers
                    )
                )
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis22A4Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_22a4_internet_subscribers 
            (year, internet_subscribers, upload_id)
            VALUES (?, ?, ?)
            ON CONFLICT (year, upload_id) DO UPDATE SET
                internet_subscribers = EXCLUDED.internet_subscribers
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setInt(1, r.year)
                setNullableLong(stmt, 2, r.internetSubscribers)
                stmt.setString(3, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun setNullableLong(stmt: PreparedStatement, index: Int, value: Long?) {
        if (value != null) stmt.setLong(index, value) else stmt.setNull(index, Types.BIGINT)
    }

    private fun parseKhmerLong(text: String?): Long? {
        if (text.isNullOrBlank() || text.trim() == "-") return null
        val khmerToLatinDigits = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
        val normalized = text.map { khmerToLatinDigits[it] ?: it }.joinToString("")
        val digitsOnly = normalized.replace(Regex("[^0-9]"), "")
        return digitsOnly.toLongOrNull()
    }

    
}