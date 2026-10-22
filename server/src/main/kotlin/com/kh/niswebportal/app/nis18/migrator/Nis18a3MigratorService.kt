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

data class Nis18a3Record(
    val museumCode: String,
    val museumKh: String,
    val year: String,
    val domesticVisitors: String?,
    val internationalVisitors: String?,
    val totalVisitors: String?
)

@Service
class Nis18a3MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_18a3"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 18 A3 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv18a3(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv18a3(inputStream: InputStream): List<Nis18a3Record> {
        val records = mutableListOf<Nis18a3Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 13) return emptyList()

            val rowYears = rows[9]
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

            for (i in 13 until rows.size) {
                val row = rows[i]

                val museumCode = row.getOrNull(1)?.trim() ?: ""
                val museumKh = row.getOrNull(2)?.trim() ?: ""

                if (museumKh.isBlank() || museumKh == "សរុប") {
                    continue
                }

                val effectiveMuseumCode = if (museumCode.isBlank() || museumCode == "18A3_") {
                    "18A3_MUSEUM_${i - 12}"
                } else {
                    museumCode
                }

                for ((year, domCol, cols) in yearColTriplets) {
                    val (intCol, totCol) = cols
                    val domVal = cleanValue(row.getOrNull(domCol))
                    val intVal = cleanValue(row.getOrNull(intCol))
                    val totVal = cleanValue(row.getOrNull(totCol))

                    records.add(
                        Nis18a3Record(
                            museumCode = effectiveMuseumCode,
                            museumKh = museumKh,
                            year = year,
                            domesticVisitors = domVal,
                            internationalVisitors = intVal,
                            totalVisitors = totVal
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis18a3Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_18a3_museum_tourist_stats
            (museum_code, museum_kh, year, domestic_visitors, international_visitors, total_visitors, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (museum_code, year, upload_id) DO UPDATE SET
                museum_kh = EXCLUDED.museum_kh,
                domestic_visitors = EXCLUDED.domestic_visitors,
                international_visitors = EXCLUDED.international_visitors,
                total_visitors = EXCLUDED.total_visitors
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.museumCode)
                stmt.setString(2, r.museumKh)
                stmt.setString(3, r.year)
                stmt.setString(4, r.domesticVisitors)
                stmt.setString(5, r.internationalVisitors)
                stmt.setString(6, r.totalVisitors)
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