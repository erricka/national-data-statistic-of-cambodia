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

data class Nis18a5Record(
    val serviceCode: String,
    val serviceKh: String,
    val year: String,
    val titleCount: String?,
    val isbnNumber: String?,
    val visitorCount: String?
)

@Service
class Nis18a5MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_18a5"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 18 A5 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv18a5(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv18a5(inputStream: InputStream): List<Nis18a5Record> {
        val records = mutableListOf<Nis18a5Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 11) return emptyList()

            val rowYears = rows[8]
            val yearColTriplets = mutableListOf<Triple<String, Int, Pair<Int, Int>>>()

            var c = 3
            while (c < rowYears.size()) {
                val yearHeader = rowYears.getOrNull(c)?.trim() ?: ""
                val yearMatch = Regex("\\d{4}").find(yearHeader.toLatinDigits())?.value
                if (yearMatch != null) {
                    yearColTriplets.add(Triple(yearMatch, c, Pair(c + 1, c + 2)))
                    c += 3
                } else {
                    c++
                }
            }

            for (i in 11 until rows.size) {
                val row = rows[i]

                val serviceCode = row.getOrNull(1)?.trim() ?: ""
                val serviceKh = row.getOrNull(2)?.trim() ?: ""

                if (serviceKh.isBlank() || serviceKh == "សរុប") {
                    continue
                }

                val effectiveServiceCode = if (serviceCode.isBlank() || serviceCode == "18A5_") {
                    "18A5_SERVICE_${i - 10}"
                } else {
                    serviceCode
                }

                for ((year, titleCol, cols) in yearColTriplets) {
                    val (isbnCol, visitorCol) = cols
                    val titleVal = cleanValue(row.getOrNull(titleCol))
                    val isbnVal = cleanValue(row.getOrNull(isbnCol))
                    val visitorVal = cleanValue(row.getOrNull(visitorCol))

                    records.add(
                        Nis18a5Record(
                            serviceCode = effectiveServiceCode,
                            serviceKh = serviceKh,
                            year = year,
                            titleCount = titleVal,
                            isbnNumber = isbnVal,
                            visitorCount = visitorVal
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis18a5Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_18a5_book_and_reading_service_stats
            (service_code, service_kh, year, title_count, isbn_number, visitor_count, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (service_code, year, upload_id) DO UPDATE SET
                service_kh = EXCLUDED.service_kh,
                title_count = EXCLUDED.title_count,
                isbn_number = EXCLUDED.isbn_number,
                visitor_count = EXCLUDED.visitor_count
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.serviceCode)
                stmt.setString(2, r.serviceKh)
                stmt.setString(3, r.year)
                stmt.setString(4, r.titleCount)
                stmt.setString(5, r.isbnNumber)
                stmt.setString(6, r.visitorCount)
                stmt.setString(7, uploadId)
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