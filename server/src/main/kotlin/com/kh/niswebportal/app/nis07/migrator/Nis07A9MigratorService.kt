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

data class Nis07A9NgoAssociationRecord(
    val year: Int,
    val categoryCode: String,
    val categoryNameKh: String,
    val y0Num: Int,
    val y0Estab: Int,
    val y0Register: Int,
    val y1New: Int,
    val y1Register: Int,
    val y1Delete: Int,
    val endYear: Int,
    val grandTotal: Int
)

@Service
class Nis07A9MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_07a9"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 07A9 import." }

        val records = file.inputStream.use { parseCsv07A9(it, defaultYear) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv07A9(inputStream: InputStream, defaultYear: Int): List<Nis07A9NgoAssociationRecord> {
        val records = mutableListOf<Nis07A9NgoAssociationRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 14) return emptyList()

            for (rowIndex in 14 until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 10) continue

                val categoryCode = row.getOrNull(0)?.trim() ?: ""
                val categoryName = row.getOrNull(1)?.trim() ?: ""

                if (categoryCode.isBlank()) continue

                records.add(
                    Nis07A9NgoAssociationRecord(
                        year = defaultYear,
                        categoryCode = categoryCode,
                        categoryNameKh = categoryName,
                        y0Num = parseKhmerInt(row.getOrNull(2)),
                        y0Estab = parseKhmerInt(row.getOrNull(3)),
                        y0Register = parseKhmerInt(row.getOrNull(4)),
                        y1New = parseKhmerInt(row.getOrNull(5)),
                        y1Register = parseKhmerInt(row.getOrNull(6)),
                        y1Delete = parseKhmerInt(row.getOrNull(7)),
                        endYear = parseKhmerInt(row.getOrNull(8)),
                        grandTotal = parseKhmerInt(row.getOrNull(9))
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

    private fun insertBatch(connection: Connection, records: List<Nis07A9NgoAssociationRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_07a9_ngo_associations
            (year, category_code, category_name_kh, y0_num, y0_estab, y0_register, y1_new, y1_register, y1_delete, end_year, grand_total, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, category_code, upload_id) DO UPDATE SET
                category_name_kh = EXCLUDED.category_name_kh,
                y0_num = EXCLUDED.y0_num,
                y0_estab = EXCLUDED.y0_estab,
                y0_register = EXCLUDED.y0_register,
                y1_new = EXCLUDED.y1_new,
                y1_register = EXCLUDED.y1_register,
                y1_delete = EXCLUDED.y1_delete,
                end_year = EXCLUDED.end_year,
                grand_total = EXCLUDED.grand_total
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setInt(1, r.year)
                stmt.setString(2, r.categoryCode)
                stmt.setString(3, r.categoryNameKh)
                stmt.setInt(4, r.y0Num)
                stmt.setInt(5, r.y0Estab)
                stmt.setInt(6, r.y0Register)
                stmt.setInt(7, r.y1New)
                stmt.setInt(8, r.y1Register)
                stmt.setInt(9, r.y1Delete)
                stmt.setInt(10, r.endYear)
                stmt.setInt(11, r.grandTotal)
                stmt.setString(12, uploadId)
                stmt.addBatch()

                count++
                if (count % BATCH_SIZE == 0) stmt.executeBatch()
            }
            if (count % BATCH_SIZE != 0) stmt.executeBatch()
        }
    }

    private fun String.toLatinDigits(): String = this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    
}