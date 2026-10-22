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

data class Nis18a10Record(
    val typeCode: String,
    val typeKh: String,
    val year: String,
    val totalCount: String?
)

@Service
class Nis18a10MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_18a10"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 18 A10 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv18a10(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv18a10(inputStream: InputStream): List<Nis18a10Record> {
        val records = mutableListOf<Nis18a10Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 8) return emptyList()

            val rowYears = rows[8]
            val yearColPairs = mutableListOf<Pair<String, Int>>()

            for (c in 3 until rowYears.size()) {
                val yearHeader = rowYears.getOrNull(c)?.trim() ?: ""
                val yearMatch = Regex("\\d{4}").find(yearHeader.toLatinDigits())?.value
                if (yearMatch != null) {
                    yearColPairs.add(Pair(yearMatch, c))
                }
            }

            for (i in 10 until rows.size) {
                val row = rows[i]

                val typeKh = row.getOrNull(2)?.trim() ?: ""

                if (typeKh.isBlank() || typeKh == "សរុប") {
                    continue
                }

                val typeCodeRaw = row.getOrNull(1)?.trim() ?: ""
                val effectiveTypeCode = if (typeCodeRaw.isBlank()) {
                    "18A10_TYPE_${i - 9}"
                } else {
                    typeCodeRaw
                }

                for ((year, colIdx) in yearColPairs) {
                    val countVal = cleanValue(row.getOrNull(colIdx))

                    records.add(
                        Nis18a10Record(
                            typeCode = effectiveTypeCode,
                            typeKh = typeKh,
                            year = year,
                            totalCount = countVal
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis18a10Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_18a10_library_and_bookstore_stats
            (type_code, type_kh, year, total_count, upload_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (type_code, year, upload_id) DO UPDATE SET
                type_kh = EXCLUDED.type_kh,
                total_count = EXCLUDED.total_count
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.typeCode)
                stmt.setString(2, r.typeKh)
                stmt.setString(3, r.year)
                stmt.setString(4, r.totalCount)
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