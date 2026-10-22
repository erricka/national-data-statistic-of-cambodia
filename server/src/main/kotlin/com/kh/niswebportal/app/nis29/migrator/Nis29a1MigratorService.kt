package com.kh.niswebportal.app.nis29.migrator

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

data class Nis29a1Record(
    val indicatorCode: String,
    val indicatorKh: String,
    val unit: String?,
    val year: String,
    val metricValue: String?
)

@Service
class Nis29a1MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_29a1"

    private companion object {
        private const val BATCH_SIZE = 1000
        private val KHMER_TO_LATIN_MAP = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )

        // Maps CSV code header row (Row 12) to Khmer metadata and unit
        private val INDICATOR_META = mapOf(
            "29a1_proj_dam" to Pair("ចំនួនគម្រោង ទំនប់", "គម្រោង"),
            "29a1_proj_canal" to Pair("ចំនួនគម្រោង ប្រឡាយ", "គម្រោង"),
            "29a1_pro" to Pair("ចំនួនរាជធានី /ខេត្ត", "រាជធានី /ខេត្ត"),
            "29a1_prio_proj" to Pair("ចំនួនគម្រោងអទិភាព", "គម្រោង"),
            "29a1_dam" to Pair("ចំនួនទំនប់", "ទំនប់"),
            "29a1_canal" to Pair("ចំនួនប្រឡាយ", "ប្រឡាយ"),
            "29a1_constr" to Pair("ចំនួនសំណង់", "សំណង់"),
            "29a1_irr_area_wet" to Pair("ផ្ទៃដីស្រោចស្រព វស្សា", "ហ.ត"),
            "29a1_irr_area_dry" to Pair("ផ្ទៃដីស្រោចស្រព ប្រាំង", "ហ.ត"),
            "29a1_irr_area_crop" to Pair("ផ្ទៃដីស្រោចស្រព ដំណាំ", "ហ.ត"),
            "29a1_oth" to Pair("ផ្សេងៗ", "រៀល")
        )
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 29 A1 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv29a1(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv29a1(inputStream: InputStream): List<Nis29a1Record> {
        val records = mutableListOf<Nis29a1Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 12) return emptyList()

            // Row 12 contains indicator codes: [ "", "29a1_y", "29a1_proj_dam", "29a1_proj_canal", ... ]
            val rowCodeHeader = rows[12]
            val colMap = mutableMapOf<Int, String>()

            for (c in 2 until rowCodeHeader.size()) {
                val code = rowCodeHeader.getOrNull(c)?.trim() ?: ""
                if (code.isNotBlank() && INDICATOR_META.containsKey(code)) {
                    colMap[c] = code
                }
            }

            // Data rows start from row index 13
            for (i in 13 until rows.size) {
                val row = rows[i]
                val seq = row.getOrNull(0)?.trim() ?: ""
                val yearVal = row.getOrNull(1)?.toLatinDigits()?.replace(",", "")?.trim() ?: ""

                // Skip summary row ("សរុប") or invalid years
                if (seq == "សរុប" || row.getOrNull(1) == "សរុប" || !yearVal.matches(Regex("^\\d{4}$"))) {
                    continue
                }

                for ((c, indCode) in colMap) {
                    val rawVal = row.getOrNull(c)
                    val meta = INDICATOR_META[indCode] ?: Pair(indCode, null)

                    records.add(
                        Nis29a1Record(
                            indicatorCode = indCode,
                            indicatorKh = meta.first,
                            unit = meta.second,
                            year = yearVal,
                            metricValue = cleanValue(rawVal)
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis29a1Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_29a1_water_resources_stats
            (indicator_code, indicator_kh, unit, year, metric_value, upload_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (indicator_code, year, upload_id) DO UPDATE SET
                indicator_kh = EXCLUDED.indicator_kh,
                unit = EXCLUDED.unit,
                metric_value = EXCLUDED.metric_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.indicatorCode)
                stmt.setString(2, r.indicatorKh)
                stmt.setString(3, r.unit)
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