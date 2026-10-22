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

data class Nis25C1VictimRecord(
    val year: String,
    val indicatorCode: String,
    val severityCategoryKh: String,
    val genderKh: String,
    val victimCount: Double?
)

@Service
class Nis25C1MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_25c1"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 25C1 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv25C1(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv25C1(inputStream: InputStream): List<Nis25C1VictimRecord> {
        val records = mutableListOf<Nis25C1VictimRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 10) return emptyList()

            val rowSeverityHeader = rows[6]
            val rowGenderHeader = rows[8]
            val rowIndicatorCode = rows[9]

            // Forward-fill severity category headers across columns
            val colMetaMap = mutableMapOf<Int, Triple<String, String, String>>()
            var currentSeverity = ""

            for (c in 2 until rowIndicatorCode.size()) {
                val sevName = rowSeverityHeader.getOrNull(c)?.trim() ?: ""
                val genderName = rowGenderHeader.getOrNull(c)?.trim() ?: ""
                val indCode = rowIndicatorCode.getOrNull(c)?.trim() ?: ""

                if (sevName.isNotBlank() && sevName != "nan") {
                    currentSeverity = sevName
                }

                if (indCode.isNotBlank() && indCode != "nan") {
                    colMetaMap[c] = Triple(indCode, currentSeverity, genderName)
                }
            }

            // Parse annual road accident victim records starting at row index 10
            for (i in 10 until rows.size) {
                val row = rows[i]
                val yearRaw = row.getOrNull(1)?.toLatinDigits()?.replace(",", "")?.trim() ?: ""

                if (yearRaw.matches(Regex("^\\d{4}$"))) {
                    for ((c, meta) in colMetaMap) {
                        val (indicatorCode, severityCategoryKh, genderKh) = meta
                        val rawVal = row.getOrNull(c)?.trim()?.replace(",", "")

                        val victimCountVal = if (rawVal.isNullOrBlank() || rawVal == "-" || rawVal == "nan") {
                            null
                        } else {
                            rawVal.toLatinDigits().toDoubleOrNull()
                        }

                        records.add(
                            Nis25C1VictimRecord(
                                year = yearRaw,
                                indicatorCode = indicatorCode,
                                severityCategoryKh = severityCategoryKh,
                                genderKh = genderKh,
                                victimCount = victimCountVal
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis25C1VictimRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_25c1_road_accident_victims_stats
            (year, indicator_code, severity_category_kh, gender_kh, victim_count, upload_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, indicator_code, upload_id) DO UPDATE SET
                severity_category_kh = EXCLUDED.severity_category_kh,
                gender_kh = EXCLUDED.gender_kh,
                victim_count = EXCLUDED.victim_count
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.year)
                stmt.setString(2, r.indicatorCode)
                stmt.setString(3, r.severityCategoryKh)
                stmt.setString(4, r.genderKh)
                if (r.victimCount != null) {
                    stmt.setDouble(5, r.victimCount)
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