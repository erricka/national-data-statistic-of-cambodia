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

data class Nis25A9ContainerRecord(
    val year: String,
    val containerTypeCode: String,
    val flowTypeNameKh: String,
    val containerTypeNameKh: String,
    val containerVolumeThousandTeus: Double?
)

@Service
class Nis25A9MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_25a9"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 25A9 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv25A9(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv25A9(inputStream: InputStream): List<Nis25A9ContainerRecord> {
        val records = mutableListOf<Nis25A9ContainerRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 10) return emptyList()

            val rowFlow = rows[6]
            val rowCode7 = rows[7]
            val rowContainerName = rows[8]
            val rowContainerCode = rows[9]

            // Fill forward flow header names across columns 2..8
            val flowHeaders = mutableMapOf<Int, String>()
            var currentFlow = ""
            for (c in 2..8) {
                val flowVal = rowFlow.getOrNull(c)?.trim() ?: ""
                if (flowVal.isNotBlank() && flowVal != "nan") {
                    currentFlow = flowVal
                }
                flowHeaders[c] = currentFlow
            }

            // Column metadata map: colIdx -> (containerTypeCode, flowTypeNameKh, containerTypeNameKh)
            val colMetaMap = mutableMapOf<Int, Triple<String, String, String>>()

            for (c in 2..8) {
                val code9 = rowContainerCode.getOrNull(c)?.trim() ?: ""
                val code7 = rowCode7.getOrNull(c)?.trim() ?: ""
                val containerCode = if (code9.isNotBlank() && code9 != "nan") code9 else code7

                val flowNameKh = flowHeaders[c] ?: ""
                var containerNameKh = rowContainerName.getOrNull(c)?.trim() ?: ""
                if (containerNameKh.isBlank() || containerNameKh == "nan") {
                    containerNameKh = "សរុប"
                }

                if (containerCode.isNotBlank() && flowNameKh.isNotBlank()) {
                    colMetaMap[c] = Triple(containerCode, flowNameKh, containerNameKh)
                }
            }

            // Parse annual volume data starting at row index 10
            for (i in 10 until rows.size) {
                val row = rows[i]
                val yearRaw = row.getOrNull(0)?.toLatinDigits()?.trim() ?: ""

                if (yearRaw.matches(Regex("^\\d{4}$"))) {
                    for ((c, meta) in colMetaMap) {
                        val (containerCode, flowNameKh, containerNameKh) = meta
                        val rawVal = row.getOrNull(c)?.trim()?.replace(",", "")

                        val volumeVal = if (rawVal.isNullOrBlank() || rawVal == "-" || rawVal == "nan") {
                            null
                        } else {
                            rawVal.toLatinDigits().toDoubleOrNull()
                        }

                        records.add(
                            Nis25A9ContainerRecord(
                                year = yearRaw,
                                containerTypeCode = containerCode,
                                flowTypeNameKh = flowNameKh,
                                containerTypeNameKh = containerNameKh,
                                containerVolumeThousandTeus = volumeVal
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis25A9ContainerRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_25a9_sihanoukville_port_container_stats
            (year, container_type_code, flow_type_name_kh, container_type_name_kh, container_volume_thousand_teus, upload_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, container_type_code, upload_id) DO UPDATE SET
                flow_type_name_kh = EXCLUDED.flow_type_name_kh,
                container_type_name_kh = EXCLUDED.container_type_name_kh,
                container_volume_thousand_teus = EXCLUDED.container_volume_thousand_teus
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.year)
                stmt.setString(2, r.containerTypeCode)
                stmt.setString(3, r.flowTypeNameKh)
                stmt.setString(4, r.containerTypeNameKh)
                if (r.containerVolumeThousandTeus != null) {
                    stmt.setDouble(5, r.containerVolumeThousandTeus)
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