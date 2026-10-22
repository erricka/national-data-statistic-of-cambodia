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

data class Nis25A7VesselRecord(
    val year: String,
    val vesselTypeCode: String,
    val vesselTypeNameKh: String,
    val vesselCount: Long?
)

@Service
class Nis25A7MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_25a7"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 25A7 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv25A7(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv25A7(inputStream: InputStream): List<Nis25A7VesselRecord> {
        val records = mutableListOf<Nis25A7VesselRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 8) return emptyList()

            val rowVesselName = rows[6]
            val rowVesselCode = rows[7]

            // Mapping: colIdx -> (VesselTypeCode, VesselTypeNameKh)
            val colMetaMap = mutableMapOf<Int, Pair<String, String>>()

            for (c in 2..7) {
                val vesselNameKh = rowVesselName.getOrNull(c)?.trim() ?: ""
                val vesselCode = rowVesselCode.getOrNull(c)?.trim() ?: ""

                if (vesselNameKh.isNotBlank() && vesselCode.isNotBlank()) {
                    colMetaMap[c] = Pair(vesselCode, vesselNameKh)
                }
            }

            // Parse annual vessel counts starting at row index 8
            for (i in 8 until rows.size) {
                val row = rows[i]
                val yearRaw = row.getOrNull(0)?.toLatinDigits()?.trim() ?: ""

                if (yearRaw.matches(Regex("^\\d{4}$"))) {
                    for ((c, meta) in colMetaMap) {
                        val (vesselCode, vesselNameKh) = meta
                        val rawVal = row.getOrNull(c)?.trim()?.replace(",", "")

                        val countVal = if (rawVal.isNullOrBlank() || rawVal == "-") {
                            null
                        } else {
                            rawVal.toLatinDigits().toLongOrNull()
                        }

                        records.add(
                            Nis25A7VesselRecord(
                                year = yearRaw,
                                vesselTypeCode = vesselCode,
                                vesselTypeNameKh = vesselNameKh,
                                vesselCount = countVal
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis25A7VesselRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_25a7_phnom_penh_port_vessel_stats
            (year, vessel_type_code, vessel_type_name_kh, vessel_count, upload_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (year, vessel_type_code, upload_id) DO UPDATE SET
                vessel_type_name_kh = EXCLUDED.vessel_type_name_kh,
                vessel_count = EXCLUDED.vessel_count
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.year)
                stmt.setString(2, r.vesselTypeCode)
                stmt.setString(3, r.vesselTypeNameKh)
                if (r.vesselCount != null) {
                    stmt.setLong(4, r.vesselCount)
                } else {
                    stmt.setNull(4, java.sql.Types.BIGINT)
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