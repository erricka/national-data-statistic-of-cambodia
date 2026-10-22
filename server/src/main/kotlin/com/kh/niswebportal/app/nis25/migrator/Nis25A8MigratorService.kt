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

data class Nis25A8CargoRecord(
    val year: String,
    val cargoTypeCode: String,
    val flowTypeNameKh: String,
    val cargoTypeNameKh: String,
    val cargoVolumeThousandTons: Double?
)

@Service
class Nis25A8MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_25a8"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 25A8 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv25A8(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv25A8(inputStream: InputStream): List<Nis25A8CargoRecord> {
        val records = mutableListOf<Nis25A8CargoRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 10) return emptyList()

            val rowFlow = rows[6]
            val rowCargoName = rows[8]
            val rowCargoCode = rows[9]

            // Fill forward flow header names across columns 2..13
            val flowHeaders = mutableMapOf<Int, String>()
            var currentFlow = ""
            for (c in 2..13) {
                val flowVal = rowFlow.getOrNull(c)?.trim() ?: ""
                if (flowVal.isNotBlank() && flowVal != "nan") {
                    currentFlow = flowVal
                }
                flowHeaders[c] = currentFlow
            }

            // Column metadata map: colIdx -> (cargoTypeCode, flowTypeNameKh, cargoTypeNameKh)
            val colMetaMap = mutableMapOf<Int, Triple<String, String, String>>()

            for (c in 2..13) {
                val cargoCode = rowCargoCode.getOrNull(c)?.trim() ?: ""
                val flowNameKh = flowHeaders[c] ?: ""
                val cargoNameKh = rowCargoName.getOrNull(c)?.trim() ?: ""

                if (cargoCode.isNotBlank() && flowNameKh.isNotBlank()) {
                    colMetaMap[c] = Triple(cargoCode, flowNameKh, cargoNameKh)
                }
            }

            // Parse annual volume data starting at row index 10
            for (i in 10 until rows.size) {
                val row = rows[i]
                val yearRaw = row.getOrNull(0)?.toLatinDigits()?.trim() ?: ""

                if (yearRaw.matches(Regex("^\\d{4}$"))) {
                    for ((c, meta) in colMetaMap) {
                        val (cargoCode, flowNameKh, cargoNameKh) = meta
                        val rawVal = row.getOrNull(c)?.trim()?.replace(",", "")

                        val volumeVal = if (rawVal.isNullOrBlank() || rawVal == "-" || rawVal == "nan") {
                            null
                        } else {
                            rawVal.toLatinDigits().toDoubleOrNull()
                        }

                        records.add(
                            Nis25A8CargoRecord(
                                year = yearRaw,
                                cargoTypeCode = cargoCode,
                                flowTypeNameKh = flowNameKh,
                                cargoTypeNameKh = cargoNameKh,
                                cargoVolumeThousandTons = volumeVal
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis25A8CargoRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_25a8_sihanoukville_port_cargo_stats
            (year, cargo_type_code, flow_type_name_kh, cargo_type_name_kh, cargo_volume_thousand_tons, upload_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, cargo_type_code, upload_id) DO UPDATE SET
                flow_type_name_kh = EXCLUDED.flow_type_name_kh,
                cargo_type_name_kh = EXCLUDED.cargo_type_name_kh,
                cargo_volume_thousand_tons = EXCLUDED.cargo_volume_thousand_tons
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.year)
                stmt.setString(2, r.cargoTypeCode)
                stmt.setString(3, r.flowTypeNameKh)
                stmt.setString(4, r.cargoTypeNameKh)
                if (r.cargoVolumeThousandTons != null) {
                    stmt.setDouble(5, r.cargoVolumeThousandTons)
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