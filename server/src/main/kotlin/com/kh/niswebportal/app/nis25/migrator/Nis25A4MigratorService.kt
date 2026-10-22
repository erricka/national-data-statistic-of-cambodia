package com.kh.niswebportal.app.nis25.migrator

import com.kh.niswebportal.core.strategy.NisMigratorStrategy
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.io.InputStreamReader
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.sql.Connection
import javax.sql.DataSource

data class Nis25A4OverloadRecord(
    val indicatorCode: String,
    val indicatorNameKh: String,
    val year: String,
    val metricValue: BigDecimal?
)

@Service
class Nis25A4MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_25a4"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 25A4 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv25A4(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv25A4(inputStream: InputStream): List<Nis25A4OverloadRecord> {
        val records = mutableListOf<Nis25A4OverloadRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 6) return emptyList()

            // Header row for years is at index 6 (Row 7)
            val headerRow = rows[6]
            val yearMap = mutableMapOf<Int, String>()

            // Columns 3 to 9 contain years (2019 - 2025)
            for (colIdx in 3 until headerRow.size()) {
                val rawYear = headerRow.getOrNull(colIdx)?.toLatinDigits()?.trim()
                if (!rawYear.isNullOrBlank()) {
                    val cleanYear = rawYear.replace(".0", "").trim()
                    if (cleanYear.matches(Regex("^\\d{4}$"))) {
                        yearMap[colIdx] = cleanYear
                    }
                }
            }

            // Iterate data rows starting from row index 7 to 11
            for (i in 7 until rows.size) {
                val row = rows[i]
                val code = row.getOrNull(0)?.trim() ?: ""

                // Handle both coded items and the total row
                val isTotalRow = row.getOrNull(1)?.trim() == "សរុប"
                val indicatorCode = if (isTotalRow) "25a4_overload_total" else code

                if (indicatorCode.startsWith("25a4_")) {
                    val indicatorNameKh = row.getOrNull(1)?.trim() ?: ""

                    for ((colIdx, year) in yearMap) {
                        val rawVal = row.getOrNull(colIdx)?.trim()?.replace(",", "")

                        val metricValue = if (rawVal.isNullOrBlank() || rawVal == "-") {
                            null
                        } else {
                            rawVal.toLatinDigits().toBigDecimalOrNull()
                        }

                        records.add(
                            Nis25A4OverloadRecord(
                                indicatorCode = indicatorCode,
                                indicatorNameKh = indicatorNameKh,
                                year = year,
                                metricValue = metricValue
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis25A4OverloadRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_25a4_overloaded_vehicles_stats
            (indicator_code, indicator_name_kh, year, metric_value, upload_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (indicator_code, year, upload_id) DO UPDATE SET
                indicator_name_kh = EXCLUDED.indicator_name_kh,
                metric_value = EXCLUDED.metric_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.indicatorCode)
                stmt.setString(2, r.indicatorNameKh)
                stmt.setString(3, r.year)
                if (r.metricValue != null) {
                    stmt.setBigDecimal(4, r.metricValue)
                } else {
                    stmt.setNull(4, java.sql.Types.NUMERIC)
                }
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

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}