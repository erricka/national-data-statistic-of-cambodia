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
import java.nio.charset.StandardCharsets
import java.sql.Connection
import javax.sql.DataSource

data class Nis16a2Record(
    val academicYear: String,
    val stuKg: String?,
    val stuPrimary: String?,
    val stuLowerSec: String?,
    val stuUpperSec: String?,
    val stuLowerSecH: String?,
    val stuUpperSecH: String?,
    val stuTotal: String?,
    val edustfKg: String?,
    val edustfPrimary: String?,
    val edustfLowerSec: String?,
    val edustfUpperSec: String?,
    val edustfLowerSecH: String?,
    val edustfUpperSecH: String?,
    val edustfTotal: String?
)

@Service
class Nis16a2MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_16a2"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for nis_16a2 import." }

        val records = file.inputStream.use { parseCsv(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                // 1. Clear previous records and history for override behavior
//                connection.prepareStatement("DELETE FROM nis_16a2").use { it.executeUpdate() }
//
//                connection.prepareStatement("DELETE FROM datasource_history WHERE source_id = ?").use { stmt ->
//                    stmt.setString(1, sourceId)
//                    stmt.executeUpdate()
//                }

                // 2. Insert new batch of records
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv(inputStream: InputStream): List<Nis16a2Record> {
        val records = mutableListOf<Nis16a2Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 11) return emptyList()

            // Data rows start at index 12 (skipping headers up to row 11)
            for (i in 12 until rows.size) {
                val row = rows[i]
                val rawYear = row.getOrNull(0)?.trim() ?: ""

                // Validate that the row represents an academic year (contains digits, avoids footnotes)
                if (rawYear.isBlank() || !rawYear.any { it.isDigit() } || rawYear.length > 20) continue

                val academicYear = cleanValue(rawYear) ?: continue

                records.add(
                    Nis16a2Record(
                        academicYear = academicYear,
                        stuKg = cleanValue(row.getOrNull(1)),
                        stuPrimary = cleanValue(row.getOrNull(2)),
                        stuLowerSec = cleanValue(row.getOrNull(3)),
                        stuUpperSec = cleanValue(row.getOrNull(4)),
                        stuLowerSecH = cleanValue(row.getOrNull(5)),
                        stuUpperSecH = cleanValue(row.getOrNull(6)),
                        stuTotal = cleanValue(row.getOrNull(7)),
                        edustfKg = cleanValue(row.getOrNull(8)),
                        edustfPrimary = cleanValue(row.getOrNull(9)),
                        edustfLowerSec = cleanValue(row.getOrNull(10)),
                        edustfUpperSec = cleanValue(row.getOrNull(11)),
                        edustfLowerSecH = cleanValue(row.getOrNull(12)),
                        edustfUpperSecH = cleanValue(row.getOrNull(13)),
                        edustfTotal = cleanValue(row.getOrNull(14))
                    )
                )
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis16a2Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_16a2
            (academic_year, stu_kg, stu_primary, stu_lower_sec, stu_upper_sec, stu_lower_sec_h, stu_upper_sec_h, stu_total, edustf_kg, edustf_primary, edustf_lower_sec, edustf_upper_sec, edustf_lower_sec_h, edustf_upper_sec_h, edustf_total, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (academic_year, upload_id) DO UPDATE SET
                stu_kg = EXCLUDED.stu_kg,
                stu_primary = EXCLUDED.stu_primary,
                stu_lower_sec = EXCLUDED.stu_lower_sec,
                stu_upper_sec = EXCLUDED.stu_upper_sec,
                stu_lower_sec_h = EXCLUDED.stu_lower_sec_h,
                stu_upper_sec_h = EXCLUDED.stu_upper_sec_h,
                stu_total = EXCLUDED.stu_total,
                edustf_kg = EXCLUDED.edustf_kg,
                edustf_primary = EXCLUDED.edustf_primary,
                edustf_lower_sec = EXCLUDED.edustf_lower_sec,
                edustf_upper_sec = EXCLUDED.edustf_upper_sec,
                edustf_lower_sec_h = EXCLUDED.edustf_lower_sec_h,
                edustf_upper_sec_h = EXCLUDED.edustf_upper_sec_h,
                edustf_total = EXCLUDED.edustf_total
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.academicYear)
                stmt.setString(2, r.stuKg)
                stmt.setString(3, r.stuPrimary)
                stmt.setString(4, r.stuLowerSec)
                stmt.setString(5, r.stuUpperSec)
                stmt.setString(6, r.stuLowerSecH)
                stmt.setString(7, r.stuUpperSecH)
                stmt.setString(8, r.stuTotal)
                stmt.setString(9, r.edustfKg)
                stmt.setString(10, r.edustfPrimary)
                stmt.setString(11, r.edustfLowerSec)
                stmt.setString(12, r.edustfUpperSec)
                stmt.setString(13, r.edustfLowerSecH)
                stmt.setString(14, r.edustfUpperSecH)
                stmt.setString(15, r.edustfTotal)
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