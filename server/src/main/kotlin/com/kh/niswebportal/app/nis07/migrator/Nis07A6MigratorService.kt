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

data class Nis07A6DetaineeRecord(
    val year: Int,
    val proCode: String,
    val provincesName: String,
    val totalDetainees: Int,
    val totalFemale: Int,
    val accusedTotal: Int,
    val accusedFemale: Int,
    val accusedForeignTotal: Int,
    val accusedForeignFemale: Int,
    val accusedJuvenileTotal: Int,
    val accusedJuvenileFemale: Int,
    val defendantsTotal: Int,
    val defendantsFemale: Int,
    val defendantsForeignTotal: Int,
    val defendantsForeignFemale: Int,
    val defendantsJuvenileTotal: Int,
    val defendantsJuvenileFemale: Int,
    val convictedUntriedTotal: Int,
    val convictedUntriedFemale: Int,
    val convictedUntriedForeignTotal: Int,
    val convictedUntriedForeignFemale: Int,
    val convictedUntriedJuvenileTotal: Int,
    val convictedUntriedJuvenileFemale: Int,
    val convictsTotal: Int,
    val convictsFemale: Int,
    val convictsForeignTotal: Int,
    val convictsForeignFemale: Int,
    val convictsJuvenileTotal: Int,
    val convictsJuvenileFemale: Int
)

@Service
class Nis07A6MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_07a6"

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
        val records = file.inputStream.use { parseCsv07A6(it, defaultYear) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv07A6(inputStream: InputStream, defaultYear: Int): List<Nis07A6DetaineeRecord> {
        val records = mutableListOf<Nis07A6DetaineeRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 13) return emptyList()

            for (rowIndex in 13 until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 28) continue

                val proCode = row.getOrNull(0)?.trim() ?: ""
                val proName = row.getOrNull(1)?.trim() ?: ""

                if (proCode.isBlank()) continue

                records.add(
                    Nis07A6DetaineeRecord(
                        year = defaultYear,
                        proCode = proCode,
                        provincesName = proName,
                        totalDetainees = parseKhmerInt(row.getOrNull(2)),
                        totalFemale = parseKhmerInt(row.getOrNull(3)),
                        accusedTotal = parseKhmerInt(row.getOrNull(4)),
                        accusedFemale = parseKhmerInt(row.getOrNull(5)),
                        accusedForeignTotal = parseKhmerInt(row.getOrNull(6)),
                        accusedForeignFemale = parseKhmerInt(row.getOrNull(7)),
                        accusedJuvenileTotal = parseKhmerInt(row.getOrNull(8)),
                        accusedJuvenileFemale = parseKhmerInt(row.getOrNull(9)),
                        defendantsTotal = parseKhmerInt(row.getOrNull(10)),
                        defendantsFemale = parseKhmerInt(row.getOrNull(11)),
                        defendantsForeignTotal = parseKhmerInt(row.getOrNull(12)),
                        defendantsForeignFemale = parseKhmerInt(row.getOrNull(13)),
                        defendantsJuvenileTotal = parseKhmerInt(row.getOrNull(14)),
                        defendantsJuvenileFemale = parseKhmerInt(row.getOrNull(15)),
                        convictedUntriedTotal = parseKhmerInt(row.getOrNull(16)),
                        convictedUntriedFemale = parseKhmerInt(row.getOrNull(17)),
                        convictedUntriedForeignTotal = parseKhmerInt(row.getOrNull(18)),
                        convictedUntriedForeignFemale = parseKhmerInt(row.getOrNull(19)),
                        convictedUntriedJuvenileTotal = parseKhmerInt(row.getOrNull(20)),
                        convictedUntriedJuvenileFemale = parseKhmerInt(row.getOrNull(21)),
                        convictsTotal = parseKhmerInt(row.getOrNull(22)),
                        convictsFemale = parseKhmerInt(row.getOrNull(23)),
                        convictsForeignTotal = parseKhmerInt(row.getOrNull(24)),
                        convictsForeignFemale = parseKhmerInt(row.getOrNull(25)),
                        convictsJuvenileTotal = parseKhmerInt(row.getOrNull(26)),
                        convictsJuvenileFemale = parseKhmerInt(row.getOrNull(27))
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

    private fun insertBatch(connection: Connection, records: List<Nis07A6DetaineeRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_07a6_detainees
            (year, pro_code, provinces_name, total_detainees, total_female, accused_total, accused_female, accused_foreign_total, accused_foreign_female, accused_juvenile_total, accused_juvenile_female, defendants_total, defendants_female, defendants_foreign_total, defendants_foreign_female, defendants_juvenile_total, defendants_juvenile_female, convicted_untried_total, convicted_untried_female, convicted_untried_foreign_total, convicted_untried_foreign_female, convicted_untried_juvenile_total, convicted_untried_juvenile_female, convicts_total, convicts_female, convicts_foreign_total, convicts_foreign_female, convicts_juvenile_total, convicts_juvenile_female, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, pro_code, upload_id) DO UPDATE SET
                provinces_name = EXCLUDED.provinces_name,
                total_detainees = EXCLUDED.total_detainees,
                total_female = EXCLUDED.total_female,
                accused_total = EXCLUDED.accused_total,
                accused_female = EXCLUDED.accused_female,
                accused_foreign_total = EXCLUDED.accused_foreign_total,
                accused_foreign_female = EXCLUDED.accused_foreign_female,
                accused_juvenile_total = EXCLUDED.accused_juvenile_total,
                accused_juvenile_female = EXCLUDED.accused_juvenile_female,
                defendants_total = EXCLUDED.defendants_total,
                defendants_female = EXCLUDED.defendants_female,
                defendants_foreign_total = EXCLUDED.defendants_foreign_total,
                defendants_foreign_female = EXCLUDED.defendants_foreign_female,
                defendants_juvenile_total = EXCLUDED.defendants_juvenile_total,
                defendants_juvenile_female = EXCLUDED.defendants_juvenile_female,
                convicted_untried_total = EXCLUDED.convicted_untried_total,
                convicted_untried_female = EXCLUDED.convicted_untried_female,
                convicted_untried_foreign_total = EXCLUDED.convicted_untried_foreign_total,
                convicted_untried_foreign_female = EXCLUDED.convicted_untried_foreign_female,
                convicted_untried_juvenile_total = EXCLUDED.convicted_untried_juvenile_total,
                convicted_untried_juvenile_female = EXCLUDED.convicted_untried_juvenile_female,
                convicts_total = EXCLUDED.convicts_total,
                convicts_female = EXCLUDED.convicts_female,
                convicts_foreign_total = EXCLUDED.convicts_foreign_total,
                convicts_foreign_female = EXCLUDED.convicts_foreign_female,
                convicts_juvenile_total = EXCLUDED.convicts_juvenile_total,
                convicts_juvenile_female = EXCLUDED.convicts_juvenile_female
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setInt(1, r.year)
                stmt.setString(2, r.proCode)
                stmt.setString(3, r.provincesName)
                stmt.setInt(4, r.totalDetainees)
                stmt.setInt(5, r.totalFemale)
                stmt.setInt(6, r.accusedTotal)
                stmt.setInt(7, r.accusedFemale)
                stmt.setInt(8, r.accusedForeignTotal)
                stmt.setInt(9, r.accusedForeignFemale)
                stmt.setInt(10, r.accusedJuvenileTotal)
                stmt.setInt(11, r.accusedJuvenileFemale)
                stmt.setInt(12, r.defendantsTotal)
                stmt.setInt(13, r.defendantsFemale)
                stmt.setInt(14, r.defendantsForeignTotal)
                stmt.setInt(15, r.defendantsForeignFemale)
                stmt.setInt(16, r.defendantsJuvenileTotal)
                stmt.setInt(17, r.defendantsJuvenileFemale)
                stmt.setInt(18, r.convictedUntriedTotal)
                stmt.setInt(19, r.convictedUntriedFemale)
                stmt.setInt(20, r.convictedUntriedForeignTotal)
                stmt.setInt(21, r.convictedUntriedForeignFemale)
                stmt.setInt(22, r.convictedUntriedJuvenileTotal)
                stmt.setInt(23, r.convictedUntriedJuvenileFemale)
                stmt.setInt(24, r.convictsTotal)
                stmt.setInt(25, r.convictsFemale)
                stmt.setInt(26, r.convictsForeignTotal)
                stmt.setInt(27, r.convictsForeignFemale)
                stmt.setInt(28, r.convictsJuvenileTotal)
                stmt.setInt(29, r.convictsJuvenileFemale)
                stmt.setString(30, uploadId)
                stmt.addBatch()

                count++
                if (count % BATCH_SIZE == 0) stmt.executeBatch()
            }
            if (count % BATCH_SIZE != 0) stmt.executeBatch()
        }
    }

    private fun String.toLatinDigits(): String = this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    
}