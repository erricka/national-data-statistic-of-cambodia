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

data class Nis27SaiPvRecord(
    val indicatorCode: String,
    val indicatorKh: String,
    val purpose: String,
    val year: String,
    val metricValue: String?
)

@Service
class Nis27ItsASraiaSaiPvMigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_27_its_a_sraia_sai_pv"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv(inputStream: InputStream): List<Nis27SaiPvRecord> {
        val records = mutableListOf<Nis27SaiPvRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 3) return emptyList()

            // Header at row index 3 contains year columns starting from index 2
            val rowHeader = rows[3]
            val yearColMap = mutableMapOf<Int, String>()

            for (c in 2 until rowHeader.size()) {
                val headerStr = rowHeader.getOrNull(c)?.trim() ?: ""
                val yearMatch = Regex("\\d{4}").find(headerStr.toLatinDigits())?.value
                if (yearMatch != null) {
                    yearColMap[c] = yearMatch
                }
            }

            // Data rows (e.g. Total, Holiday, Business, Others) start from row 4 onwards
            for (i in 4 until rows.size) {
                val row = rows[i]
                val indCode = row.getOrNull(0)?.trim() ?: ""
                val purpose = row.getOrNull(1)?.trim() ?: ""

                if (indCode.isBlank() || indCode == "nan" || purpose.isBlank() || purpose == "Percent") {
                    continue
                }

                val indicatorKh = "ស្ថិតិទេសចរអន្តរជាតិអាកាសយានដ្ឋានអន្តរជាតិសៀមរាបអង្គរ តាមគោលបំណងទស្សនកិច្ច"

                for ((c, year) in yearColMap) {
                    val rawVal = row.getOrNull(c)

                    records.add(
                        Nis27SaiPvRecord(
                            indicatorCode = indCode,
                            indicatorKh = indicatorKh,
                            purpose = purpose,
                            year = year,
                            metricValue = cleanValue(rawVal)
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis27SaiPvRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_27_its_a_sraia_sai_pv_stats
            (indicator_code, indicator_kh, purpose, year, metric_value, upload_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (indicator_code, purpose, year, upload_id) DO UPDATE SET
                indicator_kh = EXCLUDED.indicator_kh,
                metric_value = EXCLUDED.metric_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.indicatorCode)
                stmt.setString(2, r.indicatorKh)
                stmt.setString(3, r.purpose)
                stmt.setString(4, r.year)
                stmt.setString(5, r.metricValue)
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

    private fun cleanValue(value: String?): String? {
        val cleaned = value?.trim()?.replace(" ", "")?.replace(",", "")?.replace(".0", "") ?: return null
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