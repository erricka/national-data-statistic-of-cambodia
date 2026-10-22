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

data class Nis07A7ImmigrantForeignerRecord(
    val year: Int,
    val proCode: String,
    val provincesName: String,
    val numHouseholds: Int,
    val numPersonsTotal: Int,
    val numPersonsFemale: Int,
    val age18OverTotal: Int,
    val age18OverFemale: Int,
    val numNationalities: Int
)

@Service
class Nis07A7MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_07a7"

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
        val records = file.inputStream.use { parseCsv07A7(it, defaultYear) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv07A7(inputStream: InputStream, defaultYear: Int): List<Nis07A7ImmigrantForeignerRecord> {
        val records = mutableListOf<Nis07A7ImmigrantForeignerRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 12) return emptyList()

            for (rowIndex in 12 until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 8) continue

                val proCode = row.getOrNull(0)?.trim() ?: ""
                val proName = row.getOrNull(1)?.trim() ?: ""

                if (proCode.isBlank() ) continue

                records.add(
                    Nis07A7ImmigrantForeignerRecord(
                        year = defaultYear,
                        proCode = proCode,
                        provincesName = proName,
                        numHouseholds = parseKhmerInt(row.getOrNull(2)),
                        numPersonsTotal = parseKhmerInt(row.getOrNull(3)),
                        numPersonsFemale = parseKhmerInt(row.getOrNull(4)),
                        age18OverTotal = parseKhmerInt(row.getOrNull(5)),
                        age18OverFemale = parseKhmerInt(row.getOrNull(6)),
                        numNationalities = parseKhmerInt(row.getOrNull(7))
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

    private fun insertBatch(connection: Connection, records: List<Nis07A7ImmigrantForeignerRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_07a7_immigrant_foreigners
            (year, pro_code, provinces_name, num_households, num_persons_total, num_persons_female, age_18_over_total, age_18_over_female, num_nationalities, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, pro_code, upload_id) DO UPDATE SET
                provinces_name = EXCLUDED.provinces_name,
                num_households = EXCLUDED.num_households,
                num_persons_total = EXCLUDED.num_persons_total,
                num_persons_female = EXCLUDED.num_persons_female,
                age_18_over_total = EXCLUDED.age_18_over_total,
                age_18_over_female = EXCLUDED.age_18_over_female,
                num_nationalities = EXCLUDED.num_nationalities
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setInt(1, r.year)
                stmt.setString(2, r.proCode)
                stmt.setString(3, r.provincesName)
                stmt.setInt(4, r.numHouseholds)
                stmt.setInt(5, r.numPersonsTotal)
                stmt.setInt(6, r.numPersonsFemale)
                stmt.setInt(7, r.age18OverTotal)
                stmt.setInt(8, r.age18OverFemale)
                stmt.setInt(9, r.numNationalities)
                stmt.setString(10, uploadId)
                stmt.addBatch()

                count++
                if (count % BATCH_SIZE == 0) stmt.executeBatch()
            }
            if (count % BATCH_SIZE != 0) stmt.executeBatch()
        }
    }

    private fun String.toLatinDigits(): String = this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    
}