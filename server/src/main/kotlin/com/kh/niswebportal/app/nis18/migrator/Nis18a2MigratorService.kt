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

data class Nis18a2Record(
    val provinceCode: String?,
    val provinceKh: String?,
    val siteCode: String,
    val siteKh: String,
    val year: String,
    val domesticVisitors: String?,
    val internationalVisitors: String?
)

@Service
class Nis18a2MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_18a2"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 18 A2 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv18a2(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv18a2(inputStream: InputStream): List<Nis18a2Record> {
        val records = mutableListOf<Nis18a2Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 5) return emptyList()

            val rowYears = rows[2]
            val yearColPairs = mutableListOf<Triple<String, Int, Int>>()

            var c = 6
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

            var currentProvinceCode: String? = null
            var currentProvinceKh: String? = null

            for (i in 5 until rows.size) {
                val row = rows[i]

                val provCodeRaw = row.getOrNull(2)?.trim()
                val provKhRaw = row.getOrNull(3)?.trim()

                if (!provKhRaw.isNullOrBlank()) {
                    currentProvinceKh = provKhRaw
                    currentProvinceCode = if (!provCodeRaw.isNullOrBlank()) provCodeRaw else currentProvinceCode
                }

                val siteCode = row.getOrNull(4)?.trim() ?: ""
                val siteKh = row.getOrNull(5)?.trim() ?: ""

                if (siteKh.isBlank() || siteKh == "សរុប") {
                    continue
                }

                val effectiveSiteCode = if (siteCode.isBlank() || siteCode == "18A2_") {
                    "18A2_SITE_${i}"
                } else {
                    siteCode
                }

                for ((year, domCol, intCol) in yearColPairs) {
                    val domVal = cleanValue(row.getOrNull(domCol))
                    val intVal = cleanValue(row.getOrNull(intCol))

                    records.add(
                        Nis18a2Record(
                            provinceCode = currentProvinceCode,
                            provinceKh = currentProvinceKh,
                            siteCode = effectiveSiteCode,
                            siteKh = siteKh,
                            year = year,
                            domesticVisitors = domVal,
                            internationalVisitors = intVal
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis18a2Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_18a2_cultural_site_tourist_stats
            (province_code, province_kh, site_code, site_kh, year, domestic_visitors, international_visitors, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (site_code, year, upload_id) DO UPDATE SET
                province_code = EXCLUDED.province_code,
                province_kh = EXCLUDED.province_kh,
                site_kh = EXCLUDED.site_kh,
                domestic_visitors = EXCLUDED.domestic_visitors,
                international_visitors = EXCLUDED.international_visitors
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.provinceCode)
                stmt.setString(2, r.provinceKh)
                stmt.setString(3, r.siteCode)
                stmt.setString(4, r.siteKh)
                stmt.setString(5, r.year)
                stmt.setString(6, r.domesticVisitors)
                stmt.setString(7, r.internationalVisitors)
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