package com.kh.niswebportal.app.nis07.migrator

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

data class Nis07A3VitalStatisticsRecord(
    val year: Int,
    val birthTotal: Int,
    val birthFemale: Int,
    val birthCertTotal: Int,
    val birthCertFemale: Int,
    val khmerMarriageCouples: Int,
    val khmerMarriageCertCouples: Int,
    val deathTotal: Int,
    val deathFemale: Int,
    val deathCertTotal: Int,
    val deathCertFemale: Int,
    val foreignKhmerMarriageCouples: Int,
    val foreignBirthCertCount: Int,
    val foreignMarriageCouples: Int,
    val foreignDeathCount: Int
)

@Service
class Nis07A3MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_07a3"

    private companion object {
        private const val BATCH_SIZE = 1000
        private val KHMER_TO_LATIN_MAP = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val records = file.inputStream.use { inputStream ->
            parseCsv07A3(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv07A3(inputStream: InputStream): List<Nis07A3VitalStatisticsRecord> {
        val records = mutableListOf<Nis07A3VitalStatisticsRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 11) return emptyList()

            // Header titles span rows 0..10; data rows start at index 11
            for (rowIndex in 11 until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 15) continue

                val yearStr = row.getOrNull(0)?.trim() ?: ""
                val yearVal = parseKhmerInt(yearStr)

                // Skip invalid rows or headers
                if (yearVal <= 0) continue

                records.add(
                    Nis07A3VitalStatisticsRecord(
                        year = yearVal,
                        birthTotal = parseKhmerInt(row.getOrNull(1)),
                        birthFemale = parseKhmerInt(row.getOrNull(2)),
                        birthCertTotal = parseKhmerInt(row.getOrNull(3)),
                        birthCertFemale = parseKhmerInt(row.getOrNull(4)),
                        khmerMarriageCouples = parseKhmerInt(row.getOrNull(5)),
                        khmerMarriageCertCouples = parseKhmerInt(row.getOrNull(6)),
                        deathTotal = parseKhmerInt(row.getOrNull(7)),
                        deathFemale = parseKhmerInt(row.getOrNull(8)),
                        deathCertTotal = parseKhmerInt(row.getOrNull(9)),
                        deathCertFemale = parseKhmerInt(row.getOrNull(10)),
                        foreignKhmerMarriageCouples = parseKhmerInt(row.getOrNull(11)),
                        foreignBirthCertCount = parseKhmerInt(row.getOrNull(12)),
                        foreignMarriageCouples = parseKhmerInt(row.getOrNull(13)),
                        foreignDeathCount = parseKhmerInt(row.getOrNull(14))
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

    private fun insertBatch(connection: Connection, records: List<Nis07A3VitalStatisticsRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_07a3_vital_statistics
            (year, birth_total, birth_female, birth_cert_total, birth_cert_female, khmer_marriage_couples, khmer_marriage_cert_couples, death_total, death_female, death_cert_total, death_cert_female, foreign_khmer_marriage_couples, foreign_birth_cert_count, foreign_marriage_couples, foreign_death_count, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, upload_id) DO UPDATE SET
                birth_total = EXCLUDED.birth_total,
                birth_female = EXCLUDED.birth_female,
                birth_cert_total = EXCLUDED.birth_cert_total,
                birth_cert_female = EXCLUDED.birth_cert_female,
                khmer_marriage_couples = EXCLUDED.khmer_marriage_couples,
                khmer_marriage_cert_couples = EXCLUDED.khmer_marriage_cert_couples,
                death_total = EXCLUDED.death_total,
                death_female = EXCLUDED.death_female,
                death_cert_total = EXCLUDED.death_cert_total,
                death_cert_female = EXCLUDED.death_cert_female,
                foreign_khmer_marriage_couples = EXCLUDED.foreign_khmer_marriage_couples,
                foreign_birth_cert_count = EXCLUDED.foreign_birth_cert_count,
                foreign_marriage_couples = EXCLUDED.foreign_marriage_couples,
                foreign_death_count = EXCLUDED.foreign_death_count
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setInt(1, r.year)
                stmt.setInt(2, r.birthTotal)
                stmt.setInt(3, r.birthFemale)
                stmt.setInt(4, r.birthCertTotal)
                stmt.setInt(5, r.birthCertFemale)
                stmt.setInt(6, r.khmerMarriageCouples)
                stmt.setInt(7, r.khmerMarriageCertCouples)
                stmt.setInt(8, r.deathTotal)
                stmt.setInt(9, r.deathFemale)
                stmt.setInt(10, r.deathCertTotal)
                stmt.setInt(11, r.deathCertFemale)
                stmt.setInt(12, r.foreignKhmerMarriageCouples)
                stmt.setInt(13, r.foreignBirthCertCount)
                stmt.setInt(14, r.foreignMarriageCouples)
                stmt.setInt(15, r.foreignDeathCount)
                stmt.setString(16, uploadId)
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