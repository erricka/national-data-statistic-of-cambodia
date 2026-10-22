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

data class Nis25B4ProvinceLicenseRecord(
    val provinceCode: String,
    val provinceNameKh: String,
    val indicatorCode: String,
    val licenseCategoryKh: String,
    val year: String,
    val licenseCount: Double?
)

@Service
class Nis25B4MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_25b4"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 25B4 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv25B4(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv25B4(inputStream: InputStream): List<Nis25B4ProvinceLicenseRecord> {
        val records = mutableListOf<Nis25B4ProvinceLicenseRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 9) return emptyList()

            val rowCategoryHeader = rows[6]
            val rowIndicatorHeader = rows[7]
            val rowYearHeader = rows[8]

            // Determine indicator code and category name for each column dynamically
            val colMetaMap = mutableMapOf<Int, Triple<String, String, String>>()
            var currentCategory = ""
            var currentIndicatorCode = ""

            for (c in 3 until rowYearHeader.size()) {
                val catName = rowCategoryHeader.getOrNull(c)?.trim() ?: ""
                val indCode = rowIndicatorHeader.getOrNull(c)?.trim() ?: ""
                val yr = rowYearHeader.getOrNull(c)?.toLatinDigits()?.replace(",", "")?.trim() ?: ""

                if (catName.isNotBlank() && catName != "nan") {
                    currentCategory = catName
                }
                if (indCode.isNotBlank() && indCode != "nan") {
                    currentIndicatorCode = indCode
                }

                if (yr.matches(Regex("^\\d{4}$")) && currentIndicatorCode.isNotBlank()) {
                    colMetaMap[c] = Triple(currentIndicatorCode, currentCategory, yr)
                }
            }

            // Parse provincial driving license data starting at row index 9
            for (i in 9 until rows.size) {
                val row = rows[i]
                val provCode = row.getOrNull(0)?.trim() ?: ""
                val provName = row.getOrNull(1)?.trim() ?: ""

                // Process valid province rows (skip totals or empty lines)
                if (provCode.isNotBlank() && provCode != "nan" && provName.isNotBlank() && provName != "nan" && provName != "សរុប") {
                    for ((c, meta) in colMetaMap) {
                        val (indicatorCode, licenseCategoryKh, year) = meta
                        val rawVal = row.getOrNull(c)?.trim()?.replace(",", "")

                        val licenseCountVal = if (rawVal.isNullOrBlank() || rawVal == "-" || rawVal == "nan") {
                            null
                        } else {
                            rawVal.toLatinDigits().toDoubleOrNull()
                        }

                        records.add(
                            Nis25B4ProvinceLicenseRecord(
                                provinceCode = provCode,
                                provinceNameKh = provName,
                                indicatorCode = indicatorCode,
                                licenseCategoryKh = licenseCategoryKh,
                                year = year,
                                licenseCount = licenseCountVal
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis25B4ProvinceLicenseRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_25b4_driver_license_by_province_stats
            (province_code, province_name_kh, indicator_code, license_category_kh, year, license_count, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (province_code, indicator_code, year, upload_id) DO UPDATE SET
                province_name_kh = EXCLUDED.province_name_kh,
                license_category_kh = EXCLUDED.license_category_kh,
                license_count = EXCLUDED.license_count
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.provinceCode)
                stmt.setString(2, r.provinceNameKh)
                stmt.setString(3, r.indicatorCode)
                stmt.setString(4, r.licenseCategoryKh)
                stmt.setString(5, r.year)
                if (r.licenseCount != null) {
                    stmt.setDouble(6, r.licenseCount)
                } else {
                    stmt.setNull(6, java.sql.Types.NUMERIC)
                }
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

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}