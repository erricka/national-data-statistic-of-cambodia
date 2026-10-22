package com.kh.niswebportal.app.nis27.migrator

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

data class Nis27ItsATiaKtiAgrRecord(
    val indicatorCode: String,
    val indicatorKh: String,
    val year: String,
    val growthRate: String?
)

@Service
class Nis27ItsATiaKtiAgrMigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_27_its_a_tia_kti_agr"

    private companion object {
        private const val BATCH_SIZE = 1000
        private val KHMER_TO_LATIN_MAP = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
        private val MONTH_CODE_MAP = mapOf(
            "january" to "27_its_a_tia_kti_agr_jan",
            "february" to "27_its_a_tia_kti_agr_feb",
            "march" to "27_its_a_tia_kti_agr_mar",
            "april" to "27_its_a_tia_kti_agr_apr",
            "may" to "27_its_a_tia_kti_agr_may",
            "june" to "27_its_a_tia_kti_agr_jun",
            "july" to "27_its_a_tia_kti_agr_jul",
            "august" to "27_its_a_tia_kti_agr_aug",
            "september" to "27_its_a_tia_kti_agr_sep",
            "october" to "27_its_a_tia_kti_agr_oct",
            "november" to "27_its_a_tia_kti_agr_nov",
            "december" to "27_its_a_tia_kti_agr_dec"
        )
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 27_its_a_tia_kti_agr import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv27ItsATiaKtiAgr(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv27ItsATiaKtiAgr(inputStream: InputStream): List<Nis27ItsATiaKtiAgrRecord> {
        val records = mutableListOf<Nis27ItsATiaKtiAgrRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 3) return emptyList()

            // Header row with years is at row index 3
            val rowYearHeader = rows[3]

            val yearColMap = mutableMapOf<Int, String>()
            for (c in 2 until rowYearHeader.size()) {
                val yearVal = rowYearHeader.getOrNull(c)?.toLatinDigits()?.replace(",", "")?.trim() ?: ""
                if (yearVal.matches(Regex("^\\d{4}$"))) {
                    yearColMap[c] = yearVal
                }
            }

            // Data rows start from row index 4
            for (i in 4 until rows.size) {
                val row = rows[i]
                var indCode = row.getOrNull(0)?.trim() ?: ""
                val indKh = row.getOrNull(1)?.trim() ?: ""

                if (indKh.isBlank() || indKh.contains("Source:")) {
                    continue
                }

                // Fallback for missing or '#N/A' indicator codes
                if (indCode.isBlank() || indCode.contains("#N/A") || indCode == "nan") {
                    val khLower = indKh.lowercase()
                    indCode = MONTH_CODE_MAP[khLower] ?: "27_its_a_tia_kti_agr_${khLower.take(3)}"
                }

                for ((c, year) in yearColMap) {
                    val rawVal = row.getOrNull(c)

                    records.add(
                        Nis27ItsATiaKtiAgrRecord(
                            indicatorCode = indCode,
                            indicatorKh = indKh,
                            year = year,
                            growthRate = cleanValue(rawVal)
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis27ItsATiaKtiAgrRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_27_its_a_tia_kti_agr_growth_stats
            (indicator_code, indicator_kh, year, growth_rate, upload_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (indicator_code, year, upload_id) DO UPDATE SET
                indicator_kh = EXCLUDED.indicator_kh,
                growth_rate = EXCLUDED.growth_rate
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.indicatorCode)
                stmt.setString(2, r.indicatorKh)
                stmt.setString(3, r.year)
                stmt.setString(4, r.growthRate)
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

    private fun cleanValue(value: String?): String? {
        val cleaned = value?.trim()?.replace(" ", "")?.replace(",", "") ?: return null
        if (cleaned.isBlank() || cleaned == "-" || cleaned == "nan" || cleaned.contains("#N/A")) return null
        return cleaned.toLatinDigits()
    }

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}