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

data class Nis25A12PassengerRecord(
    val year: String,
    val indicatorCode: String,
    val modePortNameKh: String,
    val routeTypeNameKh: String,
    val passengerCount: Double?
)

@Service
class Nis25A12MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_25a12"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 25A12 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv25A12(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv25A12(inputStream: InputStream): List<Nis25A12PassengerRecord> {
        val records = mutableListOf<Nis25A12PassengerRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 10) return emptyList()

            val rowModeName = rows[6]
            val rowRouteName = rows[8]
            val rowIndicatorCode = rows[9]

            // Fill forward mode/port names across columns 2..6
            val modeNames = mutableMapOf<Int, String>()
            var currentModeName = ""

            for (c in 2..6) {
                val mName = rowModeName.getOrNull(c)?.trim() ?: ""
                if (mName.isNotBlank() && mName != "nan") {
                    currentModeName = mName
                }
                modeNames[c] = currentModeName
            }

            // Column metadata map: colIdx -> (indicatorCode, modePortNameKh, routeTypeNameKh)
            val colMetaMap = mutableMapOf<Int, Triple<String, String, String>>()

            for (c in 2..6) {
                val modeName = modeNames[c] ?: ""
                val routeName = rowRouteName.getOrNull(c)?.trim() ?: ""
                val code = rowIndicatorCode.getOrNull(c)?.trim() ?: ""

                if (code.isNotBlank() && code != "nan") {
                    colMetaMap[c] = Triple(code, modeName, routeName)
                }
            }

            // Parse annual passenger data starting at row index 10
            for (i in 10 until rows.size) {
                val row = rows[i]
                val yearRaw = row.getOrNull(0)?.toLatinDigits()?.trim() ?: ""

                if (yearRaw.matches(Regex("^\\d{4}$"))) {
                    for ((c, meta) in colMetaMap) {
                        val (indicatorCode, modePortNameKh, routeTypeNameKh) = meta
                        val rawVal = row.getOrNull(c)?.trim()?.replace(",", "")

                        val passengerCountVal = if (rawVal.isNullOrBlank() || rawVal == "-" || rawVal == "nan") {
                            null
                        } else {
                            rawVal.toLatinDigits().toDoubleOrNull()
                        }

                        records.add(
                            Nis25A12PassengerRecord(
                                year = yearRaw,
                                indicatorCode = indicatorCode,
                                modePortNameKh = modePortNameKh,
                                routeTypeNameKh = routeTypeNameKh,
                                passengerCount = passengerCountVal
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis25A12PassengerRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_25a12_passenger_transit_stats
            (year, indicator_code, mode_port_name_kh, route_type_name_kh, passenger_count, upload_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, indicator_code, upload_id) DO UPDATE SET
                mode_port_name_kh = EXCLUDED.mode_port_name_kh,
                route_type_name_kh = EXCLUDED.route_type_name_kh,
                passenger_count = EXCLUDED.passenger_count
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.year)
                stmt.setString(2, r.indicatorCode)
                stmt.setString(3, r.modePortNameKh)
                stmt.setString(4, r.routeTypeNameKh)
                if (r.passengerCount != null) {
                    stmt.setDouble(5, r.passengerCount)
                } else {
                    stmt.setNull(5, java.sql.Types.NUMERIC)
                }
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

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}