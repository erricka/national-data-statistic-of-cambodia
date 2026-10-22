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
import java.nio.charset.StandardCharsets
import java.sql.Connection
import javax.sql.DataSource

data class Nis25C2CauseRecord(
    val indicatorCode: String,
    val causeFactorKh: String,
    val year: String,
    val accidentCount: Double?
)

@Service
class Nis25C2MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_25c2"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 25C2 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv25C2(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv25C2(inputStream: InputStream): List<Nis25C2CauseRecord> {
        val records = mutableListOf<Nis25C2CauseRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 7) return emptyList()

            val rowYearHeader = rows[6]

            // Map column indices to years (from column 2 onwards)
            val yearColMap = mutableMapOf<Int, String>()
            for (c in 2 until rowYearHeader.size()) {
                val yearVal = rowYearHeader.getOrNull(c)?.toLatinDigits()?.replace(",", "")?.trim() ?: ""
                if (yearVal.matches(Regex("^\\d{4}$"))) {
                    yearColMap[c] = yearVal
                }
            }

            // Parse accident cause rows starting from row index 7 to 20
            for (i in 7 until rows.size) {
                val row = rows[i]
                val indCode = row.getOrNull(0)?.trim() ?: ""
                val causeFactor = row.getOrNull(1)?.trim() ?: ""

                // Stop if row is invalid or metadata footnote
                if (indCode.isBlank() || indCode == "nan" || causeFactor.contains("ប្រភព៖")) {
                    continue
                }

                for ((c, year) in yearColMap) {
                    val rawVal = row.getOrNull(c)?.trim()?.replace(",", "")

                    val accidentCountVal = if (rawVal.isNullOrBlank() || rawVal == "-" || rawVal == "nan") {
                        null
                    } else {
                        rawVal.toLatinDigits().toDoubleOrNull()
                    }

                    records.add(
                        Nis25C2CauseRecord(
                            indicatorCode = indCode,
                            causeFactorKh = causeFactor,
                            year = year,
                            accidentCount = accidentCountVal
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis25C2CauseRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_25c2_road_accident_causes_stats
            (indicator_code, cause_factor_kh, year, accident_count, upload_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (indicator_code, year, upload_id) DO UPDATE SET
                cause_factor_kh = EXCLUDED.cause_factor_kh,
                accident_count = EXCLUDED.accident_count
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.indicatorCode)
                stmt.setString(2, r.causeFactorKh)
                stmt.setString(3, r.year)
                if (r.accidentCount != null) {
                    stmt.setDouble(4, r.accidentCount)
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