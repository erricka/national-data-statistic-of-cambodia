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
import java.nio.charset.StandardCharsets
import java.sql.Connection
import javax.sql.DataSource

data class Nis07A5CrimeSuppressionRecord(
    val year: Int,
    val crimeCode: String,
    val crimeNameKh: String,
    val caseGen: Int,
    val persGen: Int,
    val caseActual: Int,
    val persActual: Int,
    val inCaseInvestigate: Int,
    val inPersInvestigate: Int,
    val priorCaseInvestigate: Int,
    val priorPersInvestigate: Int,
    val verdictCurntYear: Int,
    val verdictCurntPers: Int,
    val evidenGen: Int,
    val verdictPrevYear: Int,
    val verdictPrevPers: Int
)

@Service
class Nis07A5MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_07a5"

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
        val records = file.inputStream.use { parseCsv07A5(it, defaultYear) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv07A5(inputStream: InputStream, defaultYear: Int): List<Nis07A5CrimeSuppressionRecord> {
        val records = mutableListOf<Nis07A5CrimeSuppressionRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 13) return emptyList()

            for (rowIndex in 13 until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 15) continue

                val crimeCode = row.getOrNull(0)?.trim() ?: ""
                val crimeName = row.getOrNull(1)?.trim() ?: ""

                if (crimeCode.isBlank()) continue

                records.add(
                    Nis07A5CrimeSuppressionRecord(
                        year = defaultYear,
                        crimeCode = crimeCode,
                        crimeNameKh = crimeName,
                        caseGen = parseKhmerInt(row.getOrNull(2)),
                        persGen = parseKhmerInt(row.getOrNull(3)),
                        caseActual = parseKhmerInt(row.getOrNull(4)),
                        persActual = parseKhmerInt(row.getOrNull(5)),
                        inCaseInvestigate = parseKhmerInt(row.getOrNull(6)),
                        inPersInvestigate = parseKhmerInt(row.getOrNull(7)),
                        priorCaseInvestigate = parseKhmerInt(row.getOrNull(8)),
                        priorPersInvestigate = parseKhmerInt(row.getOrNull(9)),
                        verdictCurntYear = parseKhmerInt(row.getOrNull(10)),
                        verdictCurntPers = parseKhmerInt(row.getOrNull(11)),
                        evidenGen = parseKhmerInt(row.getOrNull(12)),
                        verdictPrevYear = parseKhmerInt(row.getOrNull(13)),
                        verdictPrevPers = parseKhmerInt(row.getOrNull(14))
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

    private fun insertBatch(connection: Connection, records: List<Nis07A5CrimeSuppressionRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_07a5_crime_suppression
            (year, crime_code, crime_name_kh, case_gen, pers_gen, case_actual, pers_actual, in_case_investigate, in_pers_investigate, prior_case_investigate, prior_pers_investigate, verdict_curnt_year, verdict_curnt_pers, eviden_gen, verdict_prev_year, verdict_prev_pers, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, crime_code, upload_id) DO UPDATE SET
                crime_name_kh = EXCLUDED.crime_name_kh,
                case_gen = EXCLUDED.case_gen,
                pers_gen = EXCLUDED.pers_gen,
                case_actual = EXCLUDED.case_actual,
                pers_actual = EXCLUDED.pers_actual,
                in_case_investigate = EXCLUDED.in_case_investigate,
                in_pers_investigate = EXCLUDED.in_pers_investigate,
                prior_case_investigate = EXCLUDED.prior_case_investigate,
                prior_pers_investigate = EXCLUDED.prior_pers_investigate,
                verdict_curnt_year = EXCLUDED.verdict_curnt_year,
                verdict_curnt_pers = EXCLUDED.verdict_curnt_pers,
                eviden_gen = EXCLUDED.eviden_gen,
                verdict_prev_year = EXCLUDED.verdict_prev_year,
                verdict_prev_pers = EXCLUDED.verdict_prev_pers
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setInt(1, r.year)
                stmt.setString(2, r.crimeCode)
                stmt.setString(3, r.crimeNameKh)
                stmt.setInt(4, r.caseGen)
                stmt.setInt(5, r.persGen)
                stmt.setInt(6, r.caseActual)
                stmt.setInt(7, r.persActual)
                stmt.setInt(8, r.inCaseInvestigate)
                stmt.setInt(9, r.inPersInvestigate)
                stmt.setInt(10, r.priorCaseInvestigate)
                stmt.setInt(11, r.priorPersInvestigate)
                stmt.setInt(12, r.verdictCurntYear)
                stmt.setInt(13, r.verdictCurntPers)
                stmt.setInt(14, r.evidenGen)
                stmt.setInt(15, r.verdictPrevYear)
                stmt.setInt(16, r.verdictPrevPers)
                stmt.setString(17, uploadId)
                stmt.addBatch()

                count++
                if (count % BATCH_SIZE == 0) stmt.executeBatch()
            }
            if (count % BATCH_SIZE != 0) stmt.executeBatch()
        }
    }

    private fun String.toLatinDigits(): String = this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    
}