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

data class Nis25B2EvRecord(
    val year: String,
    val indicatorCode: String,
    val evTypeKh: String,
    val evCount: Double?
)

@Service
class Nis25B2MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_25b2"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 25B2 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv25B2(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv25B2(inputStream: InputStream): List<Nis25B2EvRecord> {
        val records = mutableListOf<Nis25B2EvRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 8) return emptyList()

            val rowEvType = rows[6]
            val rowIndicatorCode = rows[7]

            // Column metadata map: colIdx -> (indicatorCode, evTypeKh)
            val colMetaMap = mutableMapOf<Int, Pair<String, String>>()

            for (c in 1..4) {
                val evType = rowEvType.getOrNull(c)?.trim() ?: ""
                val code = rowIndicatorCode.getOrNull(c)?.trim() ?: ""

                if (code.isNotBlank() && code != "nan") {
                    colMetaMap[c] = Pair(code, evType)
                }
            }

            // Parse annual EV registration data starting at row index 8
            for (i in 8 until rows.size) {
                val row = rows[i]
                val yearRaw = row.getOrNull(0)?.toLatinDigits()?.trim() ?: ""

                // Extract valid 4-digit yearly rows (skip 'សរុប' total row and notes)
                if (yearRaw.matches(Regex("^\\d{4}$"))) {
                    for ((c, meta) in colMetaMap) {
                        val (indicatorCode, evTypeKh) = meta
                        val rawVal = row.getOrNull(c)?.trim()?.replace(",", "")

                        val evCountVal = if (rawVal.isNullOrBlank() || rawVal == "-" || rawVal == "nan") {
                            null
                        } else {
                            rawVal.toLatinDigits().toDoubleOrNull()
                        }

                        records.add(
                            Nis25B2EvRecord(
                                year = yearRaw,
                                indicatorCode = indicatorCode,
                                evTypeKh = evTypeKh,
                                evCount = evCountVal
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis25B2EvRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_25b2_registered_ev_stats
            (year, indicator_code, ev_type_kh, ev_count, upload_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (year, indicator_code, upload_id) DO UPDATE SET
                ev_type_kh = EXCLUDED.ev_type_kh,
                ev_count = EXCLUDED.ev_count
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.year)
                stmt.setString(2, r.indicatorCode)
                stmt.setString(3, r.evTypeKh)
                if (r.evCount != null) {
                    stmt.setDouble(4, r.evCount)
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