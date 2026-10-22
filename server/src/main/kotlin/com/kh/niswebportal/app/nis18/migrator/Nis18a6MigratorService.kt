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

data class Nis18a6Record(
    val serviceCode: String,
    val serviceCategoryKh: String?,
    val serviceSubCategoryKh: String,
    val year: String,
    val songCount: String?,
    val storyCount: String?,
    val workCount: String?
)

@Service
class Nis18a6MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_18a6"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 18 A6 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv18a6(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv18a6(inputStream: InputStream): List<Nis18a6Record> {
        val records = mutableListOf<Nis18a6Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 13) return emptyList()

            val rowYears = rows[10]
            val yearColTriplets = mutableListOf<Triple<String, Int, Pair<Int, Int>>>()

            var c = 4
            while (c < rowYears.size()) {
                val yearHeader = rowYears.getOrNull(c)?.trim() ?: ""
                val yearMatch = Regex("\\d{4}").find(yearHeader.toLatinDigits())?.value
                if (yearMatch != null) {
                    yearColTriplets.add(Triple(yearMatch, c, Pair(c + 1, c + 2)))
                    c += 3
                } else {
                    c++
                }
            }

            var currentCategoryKh: String? = null

            for (i in 13 until rows.size) {
                val row = rows[i]

                val serviceCodeRaw = row.getOrNull(1)?.trim() ?: ""
                val categoryKhRaw = row.getOrNull(2)?.trim()
                val subCategoryKhRaw = row.getOrNull(3)?.trim() ?: ""

                if (!categoryKhRaw.isNullOrBlank()) {
                    currentCategoryKh = categoryKhRaw
                }

                if (subCategoryKhRaw.isBlank() || subCategoryKhRaw == "សរុប") {
                    continue
                }

                val effectiveServiceCode = if (serviceCodeRaw.isBlank() || serviceCodeRaw == "18A6_") {
                    "18A6_SERVICE_${i - 12}"
                } else {
                    serviceCodeRaw
                }

                for ((year, songCol, cols) in yearColTriplets) {
                    val (storyCol, workCol) = cols
                    val songVal = cleanValue(row.getOrNull(songCol))
                    val storyVal = cleanValue(row.getOrNull(storyCol))
                    val workVal = cleanValue(row.getOrNull(workCol))

                    records.add(
                        Nis18a6Record(
                            serviceCode = effectiveServiceCode,
                            serviceCategoryKh = currentCategoryKh,
                            serviceSubCategoryKh = subCategoryKhRaw,
                            year = year,
                            songCount = songVal,
                            storyCount = storyVal,
                            workCount = workVal
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis18a6Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_18a6_copyright_services_stats
            (service_code, service_category_kh, service_sub_category_kh, year, song_count, story_count, work_count, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (service_code, year, upload_id) DO UPDATE SET
                service_category_kh = EXCLUDED.service_category_kh,
                service_sub_category_kh = EXCLUDED.service_sub_category_kh,
                song_count = EXCLUDED.song_count,
                story_count = EXCLUDED.story_count,
                work_count = EXCLUDED.work_count
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.serviceCode)
                stmt.setString(2, r.serviceCategoryKh)
                stmt.setString(3, r.serviceSubCategoryKh)
                stmt.setString(4, r.year)
                stmt.setString(5, r.songCount)
                stmt.setString(6, r.storyCount)
                stmt.setString(7, r.workCount)
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