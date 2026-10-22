package com.kh.niswebportal.app.nis07.migrator

import com.kh.niswebportal.core.util.getOrNull
import com.kh.niswebportal.core.strategy.NisMigratorStrategy
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.io.InputStreamReader
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.sql.Connection
import javax.sql.DataSource

data class Nis07A11TrafficAccidentRecord(
    val year: Int,
    val proCode: String,
    val provincesName: String,
    val accidentCurrentYear: Int,
    val accidentPrevYear: Int,
    val accidentGrowth: Int,
    val accidentPercent: BigDecimal,
    val deathCurrentYear: Int,
    val deathPrevYear: Int,
    val deathGrowth: Int,
    val deathPercent: BigDecimal,
    val injuryCurrentYear: Int,
    val injuryPrevYear: Int,
    val injuryGrowth: Int,
    val injuryPercent: BigDecimal,
    val heavyInjuryCurrentYear: Int,
    val heavyInjuryPrevYear: Int,
    val heavyInjuryGrowth: Int,
    val heavyInjuryPercent: BigDecimal,
    val lightInjuryCurrentYear: Int,
    val lightInjuryPrevYear: Int,
    val lightInjuryGrowth: Int,
    val lightInjuryPercent: BigDecimal
)

@Service
class Nis07A11MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_07a11"

    private companion object {
        private const val BATCH_SIZE = 1000
        private val KHMER_TO_LATIN_MAP = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val defaultYear = 2023
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 07A11 import." }

        val records = file.inputStream.use { parseCsv07A11(it, defaultYear) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv07A11(inputStream: InputStream, defaultYear: Int): List<Nis07A11TrafficAccidentRecord> {
        val records = mutableListOf<Nis07A11TrafficAccidentRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 13) return emptyList()

            for (rowIndex in 13 until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 22) continue

                val proCode = row.getOrNull(0)?.trim() ?: ""
                val proName = row.getOrNull(1)?.trim() ?: ""

                if (proCode.isBlank() ) continue

                records.add(
                    Nis07A11TrafficAccidentRecord(
                        year = defaultYear,
                        proCode = proCode,
                        provincesName = proName,
                        accidentCurrentYear = parseKhmerInt(row.getOrNull(2)),
                        accidentPrevYear = parseKhmerInt(row.getOrNull(3)),
                        accidentGrowth = parseKhmerInt(row.getOrNull(4)),
                        accidentPercent = parseKhmerBigDecimal(row.getOrNull(5)),

                        deathCurrentYear = parseKhmerInt(row.getOrNull(6)),
                        deathPrevYear = parseKhmerInt(row.getOrNull(7)),
                        deathGrowth = parseKhmerInt(row.getOrNull(8)),
                        deathPercent = parseKhmerBigDecimal(row.getOrNull(9)),

                        injuryCurrentYear = parseKhmerInt(row.getOrNull(10)),
                        injuryPrevYear = parseKhmerInt(row.getOrNull(11)),
                        injuryGrowth = parseKhmerInt(row.getOrNull(12)),
                        injuryPercent = parseKhmerBigDecimal(row.getOrNull(13)),

                        heavyInjuryCurrentYear = parseKhmerInt(row.getOrNull(14)),
                        heavyInjuryPrevYear = parseKhmerInt(row.getOrNull(15)),
                        heavyInjuryGrowth = parseKhmerInt(row.getOrNull(16)),
                        heavyInjuryPercent = parseKhmerBigDecimal(row.getOrNull(17)),

                        lightInjuryCurrentYear = parseKhmerInt(row.getOrNull(18)),
                        lightInjuryPrevYear = parseKhmerInt(row.getOrNull(19)),
                        lightInjuryGrowth = parseKhmerInt(row.getOrNull(20)),
                        lightInjuryPercent = parseKhmerBigDecimal(row.getOrNull(21))
                    )
                )
            }
        }
        return records
    }

    private fun parseKhmerInt(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        val trimmed = text.trim()
        if (trimmed == "-" || trimmed.contains("-")) return 0
        val normalized = trimmed.toLatinDigits()
        val cleanNumber = normalized.replace(Regex("[^0-9]"), "")
        return cleanNumber.toIntOrNull() ?: 0
    }

    private fun parseKhmerBigDecimal(text: String?): BigDecimal {
        if (text.isNullOrBlank()) return BigDecimal.ZERO
        val trimmed = text.trim().replace("%", "")
        if (trimmed == "-" || trimmed.contains("-")) return BigDecimal.ZERO
        val normalized = trimmed.toLatinDigits()
        val cleanNumber = normalized.replace(Regex("[^0-9.]"), "")
        return cleanNumber.toBigDecimalOrNull() ?: BigDecimal.ZERO
    }

    private fun insertBatch(connection: Connection, records: List<Nis07A11TrafficAccidentRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_07a11_traffic_accidents
            (year, pro_code, provinces_name, accident_current_year, accident_prev_year, accident_growth, accident_percent, death_current_year, death_prev_year, death_growth, death_percent, injury_current_year, injury_prev_year, injury_growth, injury_percent, heavy_injury_current_year, heavy_injury_prev_year, heavy_injury_growth, heavy_injury_percent, light_injury_current_year, light_injury_prev_year, light_injury_growth, light_injury_percent, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, pro_code, upload_id) DO UPDATE SET
                provinces_name = EXCLUDED.provinces_name,
                accident_current_year = EXCLUDED.accident_current_year,
                accident_prev_year = EXCLUDED.accident_prev_year,
                accident_growth = EXCLUDED.accident_growth,
                accident_percent = EXCLUDED.accident_percent,
                death_current_year = EXCLUDED.death_current_year,
                death_prev_year = EXCLUDED.death_prev_year,
                death_growth = EXCLUDED.death_growth,
                death_percent = EXCLUDED.death_percent,
                injury_current_year = EXCLUDED.injury_current_year,
                injury_prev_year = EXCLUDED.injury_prev_year,
                injury_growth = EXCLUDED.injury_growth,
                injury_percent = EXCLUDED.injury_percent,
                heavy_injury_current_year = EXCLUDED.heavy_injury_current_year,
                heavy_injury_prev_year = EXCLUDED.heavy_injury_prev_year,
                heavy_injury_growth = EXCLUDED.heavy_injury_growth,
                heavy_injury_percent = EXCLUDED.heavy_injury_percent,
                light_injury_current_year = EXCLUDED.light_injury_current_year,
                light_injury_prev_year = EXCLUDED.light_injury_prev_year,
                light_injury_growth = EXCLUDED.light_injury_growth,
                light_injury_percent = EXCLUDED.light_injury_percent
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setInt(1, r.year)
                stmt.setString(2, r.proCode)
                stmt.setString(3, r.provincesName)
                stmt.setInt(4, r.accidentCurrentYear)
                stmt.setInt(5, r.accidentPrevYear)
                stmt.setInt(6, r.accidentGrowth)
                stmt.setBigDecimal(7, r.accidentPercent)
                stmt.setInt(8, r.deathCurrentYear)
                stmt.setInt(9, r.deathPrevYear)
                stmt.setInt(10, r.deathGrowth)
                stmt.setBigDecimal(11, r.deathPercent)
                stmt.setInt(12, r.injuryCurrentYear)
                stmt.setInt(13, r.injuryPrevYear)
                stmt.setInt(14, r.injuryGrowth)
                stmt.setBigDecimal(15, r.injuryPercent)
                stmt.setInt(16, r.heavyInjuryCurrentYear)
                stmt.setInt(17, r.heavyInjuryPrevYear)
                stmt.setInt(18, r.heavyInjuryGrowth)
                stmt.setBigDecimal(19, r.heavyInjuryPercent)
                stmt.setInt(20, r.lightInjuryCurrentYear)
                stmt.setInt(21, r.lightInjuryPrevYear)
                stmt.setInt(22, r.lightInjuryGrowth)
                stmt.setBigDecimal(23, r.lightInjuryPercent)
                stmt.setString(24, uploadId)
                stmt.addBatch()

                count++
                if (count % BATCH_SIZE == 0) stmt.executeBatch()
            }
            if (count % BATCH_SIZE != 0) stmt.executeBatch()
        }
    }

    private fun String.toLatinDigits(): String = this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    
}