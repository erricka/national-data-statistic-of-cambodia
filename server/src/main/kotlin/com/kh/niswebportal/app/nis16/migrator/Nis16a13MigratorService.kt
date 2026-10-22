package com.kh.niswebportal.app.nis16.migrator

import com.kh.niswebportal.core.strategy.NisMigratorStrategy
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.io.InputStreamReader
import java.sql.Connection
import javax.sql.DataSource

data class Nis16a13Record(
    val year: String,
    val gender: String,
    val kindergarten: String?,
    val primarySchool: String?,
    val lowerSecondary: String?,
    val upperSecondary: String?,
    val total: String?
)

@Service
class Nis16a13MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_16a13"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for nis_16a13 import." }

        val records = file.inputStream.use { parseCsv(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                // 1. Clear previous records and history for override behavior
//                connection.prepareStatement("DELETE FROM nis_16a13").use { it.executeUpdate() }
//
//                connection.prepareStatement("DELETE FROM datasource_history WHERE source_id = ?").use { stmt ->
//                    stmt.setString(1, sourceId)
//                    stmt.executeUpdate()
//                }

                // 2. Insert new batch of records with ON CONFLICT update support
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv(inputStream: InputStream): List<Nis16a13Record> {
        val records = mutableListOf<Nis16a13Record>()

        InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 8) return emptyList()

            var currentGender = "BOTH"

            for (i in 9 until rows.size) {
                val row = rows[i]
                val col0 = row.getOrNull(0)?.trim() ?: ""

                if (col0.isBlank() || col0.lowercase() == "nan") continue

                // Check section headers for gender switches
                if (col0.contains("ប្រុស")) {
                    currentGender = "MALE"
                    continue
                } else if (col0.contains("ស្រី")) {
                    currentGender = "FEMALE"
                    continue
                } else if (col0.contains("ប្រភព") || col0.contains("Source")) {
                    break
                }

                val year = cleanValue(col0)?.substringBefore(".") ?: continue
                val kg = cleanValue(row.getOrNull(1))
                val primary = cleanValue(row.getOrNull(2))
                val lowerSec = cleanValue(row.getOrNull(3))
                val upperSec = cleanValue(row.getOrNull(4))
                val total = cleanValue(row.getOrNull(5))

                records.add(
                    Nis16a13Record(
                        year = year,
                        gender = currentGender,
                        kindergarten = kg,
                        primarySchool = primary,
                        lowerSecondary = lowerSec,
                        upperSecondary = upperSec,
                        total = total
                    )
                )
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis16a13Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_16a13
            (year, gender, kindergarten, primary_school, lower_secondary, upper_secondary, total, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, gender, upload_id) DO UPDATE SET
                kindergarten = EXCLUDED.kindergarten,
                primary_school = EXCLUDED.primary_school,
                lower_secondary = EXCLUDED.lower_secondary,
                upper_secondary = EXCLUDED.upper_secondary,
                total = EXCLUDED.total
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.year)
                stmt.setString(2, r.gender)
                stmt.setString(3, r.kindergarten)
                stmt.setString(4, r.primarySchool)
                stmt.setString(5, r.lowerSecondary)
                stmt.setString(6, r.upperSecondary)
                stmt.setString(7, r.total)
                stmt.setString(8, uploadId)
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
        if (cleaned.isBlank() || cleaned == "-" || cleaned.lowercase() == "n.a" || cleaned.lowercase() == "nan") return null
        return cleaned.toLatinDigits()
    }

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}