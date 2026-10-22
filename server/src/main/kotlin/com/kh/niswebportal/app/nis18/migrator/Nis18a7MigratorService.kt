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

data class Nis18a7Record(
    val categoryCode: String?,
    val categoryKh: String?,
    val subCategoryCode: String,
    val subCategoryKh: String,
    val year: String,
    val titleCount: String?,
    val licenseCount: String?
)

@Service
class Nis18a7MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_18a7"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 18 A7 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv18a7(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv18a7(inputStream: InputStream): List<Nis18a7Record> {
        val records = mutableListOf<Nis18a7Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 14) return emptyList()

            val rowYears = rows[11]
            val yearColPairs = mutableListOf<Triple<String, Int, Int>>()

            var c = 5
            while (c < rowYears.size()) {
                val yearHeader = rowYears.getOrNull(c)?.trim() ?: ""
                val yearMatch = Regex("\\d{4}").find(yearHeader.toLatinDigits())?.value
                if (yearMatch != null) {
                    yearColPairs.add(Triple(yearMatch, c, c + 1))
                    c += 2
                } else {
                    c++
                }
            }

            var currentCategoryCode: String? = null
            var currentCategoryKh: String? = null

            for (i in 14 until rows.size) {
                val row = rows[i]

                val catCodeRaw = row.getOrNull(1)?.trim()
                val catKhRaw = row.getOrNull(2)?.trim()
                val subCodeRaw = row.getOrNull(3)?.trim() ?: ""
                val subKhRaw = row.getOrNull(4)?.trim() ?: ""

                val firstColVal = row.getOrNull(0)?.trim() ?: ""
                if (firstColVal == "សរុប" || catKhRaw == "សរុប" || subKhRaw == "សរុប") {
                    continue
                }

                if (!catKhRaw.isNullOrBlank()) {
                    currentCategoryCode = catCodeRaw
                    currentCategoryKh = catKhRaw
                }

                val effectiveSubKh = if (subKhRaw.isNotBlank()) {
                    subKhRaw
                } else {
                    currentCategoryKh ?: ""
                }

                val effectiveSubCode = if (subCodeRaw.isNotBlank()) {
                    subCodeRaw
                } else if (!currentCategoryCode.isNullOrBlank()) {
                    currentCategoryCode
                } else {
                    "18A7_SERVICE_${i - 13}"
                }

                if (effectiveSubKh.isBlank()) {
                    continue
                }

                for ((year, titleCol, licenseCol) in yearColPairs) {
                    val titleVal = cleanValue(row.getOrNull(titleCol))
                    val licenseVal = cleanValue(row.getOrNull(licenseCol))

                    records.add(
                        Nis18a7Record(
                            categoryCode = currentCategoryCode,
                            categoryKh = currentCategoryKh,
                            subCategoryCode = effectiveSubCode,
                            subCategoryKh = effectiveSubKh,
                            year = year,
                            titleCount = titleVal,
                            licenseCount = licenseVal
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis18a7Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_18a7_film_and_video_service_stats
            (category_code, category_kh, sub_category_code, sub_category_kh, year, title_count, license_count, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (sub_category_code, year, upload_id) DO UPDATE SET
                category_code = EXCLUDED.category_code,
                category_kh = EXCLUDED.category_kh,
                sub_category_kh = EXCLUDED.sub_category_kh,
                title_count = EXCLUDED.title_count,
                license_count = EXCLUDED.license_count
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.categoryCode)
                stmt.setString(2, r.categoryKh)
                stmt.setString(3, r.subCategoryCode)
                stmt.setString(4, r.subCategoryKh)
                stmt.setString(5, r.year)
                stmt.setString(6, r.titleCount)
                stmt.setString(7, r.licenseCount)
                stmt.setString(8, uploadId)
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