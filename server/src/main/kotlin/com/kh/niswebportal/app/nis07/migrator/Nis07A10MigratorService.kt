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

data class Nis07A10AdminDivisionRecord(
    val year: Int,
    val proCode: String,
    val provincesName: String,
    val numKhan: Int,
    val numKrong: Int,
    val numDistrict: Int,
    val totalDistrictLevel: Int,
    val numSangkat: Int,
    val numCommune: Int,
    val totalCommuneLevel: Int,
    val oldVillages: Int,
    val newVillages: Int,
    val deletedVillages: Int,
    val grandTotalVillages: Int
)

@Service
class Nis07A10MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_07a10"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 07A10 import." }

        val records = file.inputStream.use { parseCsv07A10(it, defaultYear) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv07A10(inputStream: InputStream, defaultYear: Int): List<Nis07A10AdminDivisionRecord> {
        val records = mutableListOf<Nis07A10AdminDivisionRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 13) return emptyList()

            for (rowIndex in 13 until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 14) continue

                val proCode = row.getOrNull(0)?.trim() ?: ""
                val proName = row.getOrNull(1)?.trim() ?: ""

                if (proCode.isBlank() ) continue

                records.add(
                    Nis07A10AdminDivisionRecord(
                        year = defaultYear,
                        proCode = proCode,
                        provincesName = proName,
                        numKhan = parseKhmerInt(row.getOrNull(2)),
                        numKrong = parseKhmerInt(row.getOrNull(3)),
                        numDistrict = parseKhmerInt(row.getOrNull(4)),
                        totalDistrictLevel = parseKhmerInt(row.getOrNull(5)),
                        numSangkat = parseKhmerInt(row.getOrNull(6)),
                        numCommune = parseKhmerInt(row.getOrNull(7)),
                        totalCommuneLevel = parseKhmerInt(row.getOrNull(8)),
                        oldVillages = parseKhmerInt(row.getOrNull(9)),
                        newVillages = parseKhmerInt(row.getOrNull(10)),
                        deletedVillages = parseKhmerInt(row.getOrNull(11)),
                        grandTotalVillages = parseKhmerInt(row.getOrNull(12))
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

    private fun insertBatch(connection: Connection, records: List<Nis07A10AdminDivisionRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_07a10_administrative_divisions
            (year, pro_code, provinces_name, num_khan, num_krong, num_district, total_district_level, num_sangkat, num_commune, total_commune_level, old_villages, new_villages, deleted_villages, grand_total_villages, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, pro_code, upload_id) DO UPDATE SET
                provinces_name = EXCLUDED.provinces_name,
                num_khan = EXCLUDED.num_khan,
                num_krong = EXCLUDED.num_krong,
                num_district = EXCLUDED.num_district,
                total_district_level = EXCLUDED.total_district_level,
                num_sangkat = EXCLUDED.num_sangkat,
                num_commune = EXCLUDED.num_commune,
                total_commune_level = EXCLUDED.total_commune_level,
                old_villages = EXCLUDED.old_villages,
                new_villages = EXCLUDED.new_villages,
                deleted_villages = EXCLUDED.deleted_villages,
                grand_total_villages = EXCLUDED.grand_total_villages
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setInt(1, r.year)
                stmt.setString(2, r.proCode)
                stmt.setString(3, r.provincesName)
                stmt.setInt(4, r.numKhan)
                stmt.setInt(5, r.numKrong)
                stmt.setInt(6, r.numDistrict)
                stmt.setInt(7, r.totalDistrictLevel)
                stmt.setInt(8, r.numSangkat)
                stmt.setInt(9, r.numCommune)
                stmt.setInt(10, r.totalCommuneLevel)
                stmt.setInt(11, r.oldVillages)
                stmt.setInt(12, r.newVillages)
                stmt.setInt(13, r.deletedVillages)
                stmt.setInt(14, r.grandTotalVillages)
                stmt.setString(15, uploadId)
                stmt.addBatch()

                count++
                if (count % BATCH_SIZE == 0) stmt.executeBatch()
            }
            if (count % BATCH_SIZE != 0) stmt.executeBatch()
        }
    }

    private fun String.toLatinDigits(): String = this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    
}