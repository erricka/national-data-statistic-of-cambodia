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

data class Nis07A1RegisteredPopulationRecord(
    val year: Int,
    val provinceCode: String,
    val provinceName: String,
    val numHouseholds: Int,
    val totalMembers: Int,
    val femaleMembers: Int,
    val age15OverTotal: Int,
    val age15OverFemale: Int,
    val age18OverTotal: Int,
    val age18OverFemale: Int
)

@Service
class Nis07A1MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_07a1"

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
        val records = file.inputStream.use { inputStream ->
            parseCsv07A1(inputStream, defaultYear)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv07A1(inputStream: InputStream, defaultYear: Int): List<Nis07A1RegisteredPopulationRecord> {
        val records = mutableListOf<Nis07A1RegisteredPopulationRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 13) return emptyList()

            for (rowIndex in 13 until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 9) continue

                val proCode = row.getOrNull(0)?.trim() ?: ""
                val proName = row.getOrNull(1)?.trim() ?: ""

                if (proCode.isBlank() ) continue

                records.add(
                    Nis07A1RegisteredPopulationRecord(
                        year = defaultYear,
                        provinceCode = proCode,
                        provinceName = proName,
                        numHouseholds = parseKhmerInt(row.getOrNull(2)),
                        totalMembers = parseKhmerInt(row.getOrNull(3)),
                        femaleMembers = parseKhmerInt(row.getOrNull(4)),
                        age15OverTotal = parseKhmerInt(row.getOrNull(5)),
                        age15OverFemale = parseKhmerInt(row.getOrNull(6)),
                        age18OverTotal = parseKhmerInt(row.getOrNull(7)),
                        age18OverFemale = parseKhmerInt(row.getOrNull(8))
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

    private fun insertBatch(connection: Connection, records: List<Nis07A1RegisteredPopulationRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_07a1_registered_population
            (year, province_code, province_name, num_households, total_members, female_members, age_15_over_total, age_15_over_female, age_18_over_total, age_18_over_female, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, province_code, upload_id) DO UPDATE SET
                province_name = EXCLUDED.province_name,
                num_households = EXCLUDED.num_households,
                total_members = EXCLUDED.total_members,
                female_members = EXCLUDED.female_members,
                age_15_over_total = EXCLUDED.age_15_over_total,
                age_15_over_female = EXCLUDED.age_15_over_female,
                age_18_over_total = EXCLUDED.age_18_over_total,
                age_18_over_female = EXCLUDED.age_18_over_female
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setInt(1, r.year)
                stmt.setString(2, r.provinceCode)
                stmt.setString(3, r.provinceName)
                stmt.setInt(4, r.numHouseholds)
                stmt.setInt(5, r.totalMembers)
                stmt.setInt(6, r.femaleMembers)
                stmt.setInt(7, r.age15OverTotal)
                stmt.setInt(8, r.age15OverFemale)
                stmt.setInt(9, r.age18OverTotal)
                stmt.setInt(10, r.age18OverFemale)
                stmt.setString(11, uploadId)
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