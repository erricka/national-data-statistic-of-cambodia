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

data class Nis25B3LicenseRecord(
    val year: String,
    val indicatorCode: String,
    val categoryGroupKh: String,
    val categoryNameKh: String,
    val licenseCount: Double?
)

@Service
class Nis25B3MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_25b3"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 25B3 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv25B3(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv25B3(inputStream: InputStream): List<Nis25B3LicenseRecord> {
        val records = mutableListOf<Nis25B3LicenseRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 10) return emptyList()

            val rowGroupHeader = rows[6]
            val rowSubHeader = rows[8]
            val rowIndicatorCode = rows[9]

            // Forward-fill category groups across columns 1..6
            val groupNames = mutableMapOf<Int, String>()
            var currentGroup = ""

            for (c in 1..6) {
                val gName = rowGroupHeader.getOrNull(c)?.trim() ?: ""
                if (gName.isNotBlank() && gName != "nan") {
                    currentGroup = gName
                }
                groupNames[c] = currentGroup
            }

            // Column metadata map: colIdx -> (indicatorCode, categoryGroupKh, categoryNameKh)
            val colMetaMap = mutableMapOf<Int, Triple<String, String, String>>()

            for (c in 1..6) {
                val groupName = groupNames[c] ?: ""
                val categoryName = rowSubHeader.getOrNull(c)?.trim() ?: ""
                val code = rowIndicatorCode.getOrNull(c)?.trim() ?: ""

                if (code.isNotBlank() && code != "nan") {
                    colMetaMap[c] = Triple(code, groupName, categoryName)
                }
            }

            // Parse annual driving license data starting at row index 10
            for (i in 10 until rows.size) {
                val row = rows[i]
                val yearRaw = row.getOrNull(0)?.toLatinDigits()?.trim() ?: ""

                if (yearRaw.matches(Regex("^\\d{4}$"))) {
                    for ((c, meta) in colMetaMap) {
                        val (indicatorCode, categoryGroupKh, categoryNameKh) = meta
                        val rawVal = row.getOrNull(c)?.trim()?.replace(",", "")

                        val licenseCountVal = if (rawVal.isNullOrBlank() || rawVal == "-" || rawVal == "nan") {
                            null
                        } else {
                            rawVal.toLatinDigits().toDoubleOrNull()
                        }

                        records.add(
                            Nis25B3LicenseRecord(
                                year = yearRaw,
                                indicatorCode = indicatorCode,
                                categoryGroupKh = categoryGroupKh,
                                categoryNameKh = categoryNameKh,
                                licenseCount = licenseCountVal
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis25B3LicenseRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_25b3_driver_license_stats
            (year, indicator_code, category_group_kh, category_name_kh, license_count, upload_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, indicator_code, upload_id) DO UPDATE SET
                category_group_kh = EXCLUDED.category_group_kh,
                category_name_kh = EXCLUDED.category_name_kh,
                license_count = EXCLUDED.license_count
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.year)
                stmt.setString(2, r.indicatorCode)
                stmt.setString(3, r.categoryGroupKh)
                stmt.setString(4, r.categoryNameKh)
                if (r.licenseCount != null) {
                    stmt.setDouble(5, r.licenseCount)
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