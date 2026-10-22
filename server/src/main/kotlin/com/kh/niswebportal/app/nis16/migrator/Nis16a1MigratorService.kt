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

data class Nis16a1Record(
    val academicYear: String,
    val schKg: String?,
    val schPrimary: String?,
    val schLowerSec: String?,
    val schUpperSec: String?,
    val schTotal: String?,
    val clsKg: String?,
    val clsPrimary: String?,
    val clsLowerSec: String?,
    val clsUpperSec: String?,
    val clsTotal: String?
)

@Service
class Nis16a1MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_16a1"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for nis_16a1 import." }

        val records = file.inputStream.use { parseCsv(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                // 1. Clear previous records and history for override behavior
//                connection.prepareStatement("DELETE FROM nis_16a1").use { it.executeUpdate() }
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

    private fun parseCsv(inputStream: InputStream): List<Nis16a1Record> {
        val records = mutableListOf<Nis16a1Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 10) return emptyList()

            // Data rows start at index 11 (skipping headers up to row 10)
            for (i in 11 until rows.size) {
                val row = rows[i]
                val rawYear = row.getOrNull(0)?.trim() ?: ""

                // Validate that the row represents an academic year (e.g., must contain digits like '1979-80' or '2023-24')
                // This automatically skips footer source notes or blank rows.
                if (rawYear.isBlank() || !rawYear.any { it.isDigit() } || rawYear.length > 20) continue

                val academicYear = cleanValue(rawYear) ?: continue

                records.add(
                    Nis16a1Record(
                        academicYear = academicYear,
                        schKg = cleanValue(row.getOrNull(1)),
                        schPrimary = cleanValue(row.getOrNull(2)),
                        schLowerSec = cleanValue(row.getOrNull(3)),
                        schUpperSec = cleanValue(row.getOrNull(4)),
                        schTotal = cleanValue(row.getOrNull(5)),
                        clsKg = cleanValue(row.getOrNull(6)),
                        clsPrimary = cleanValue(row.getOrNull(7)),
                        clsLowerSec = cleanValue(row.getOrNull(8)),
                        clsUpperSec = cleanValue(row.getOrNull(9)),
                        clsTotal = cleanValue(row.getOrNull(10))
                    )
                )
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis16a1Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_16a1
            (academic_year, sch_kg, sch_primary, sch_lower_sec, sch_upper_sec, sch_total, cls_kg, cls_primary, cls_lower_sec, cls_upper_sec, cls_total, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (academic_year, upload_id) DO UPDATE SET
                sch_kg = EXCLUDED.sch_kg,
                sch_primary = EXCLUDED.sch_primary,
                sch_lower_sec = EXCLUDED.sch_lower_sec,
                sch_upper_sec = EXCLUDED.sch_upper_sec,
                sch_total = EXCLUDED.sch_total,
                cls_kg = EXCLUDED.cls_kg,
                cls_primary = EXCLUDED.cls_primary,
                cls_lower_sec = EXCLUDED.cls_lower_sec,
                cls_upper_sec = EXCLUDED.cls_upper_sec,
                cls_total = EXCLUDED.cls_total
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.academicYear)
                stmt.setString(2, r.schKg)
                stmt.setString(3, r.schPrimary)
                stmt.setString(4, r.schLowerSec)
                stmt.setString(5, r.schUpperSec)
                stmt.setString(6, r.schTotal)
                stmt.setString(7, r.clsKg)
                stmt.setString(8, r.clsPrimary)
                stmt.setString(9, r.clsLowerSec)
                stmt.setString(10, r.clsUpperSec)
                stmt.setString(11, r.clsTotal)
                stmt.setString(12, uploadId)
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