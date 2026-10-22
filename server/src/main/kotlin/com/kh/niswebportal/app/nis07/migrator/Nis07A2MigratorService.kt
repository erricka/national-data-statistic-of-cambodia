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

data class Nis07A2NationalIdCardRecord(
    val year: Int,
    val provinceCode: String,
    val provinceName: String,
    val planToProvide: Int,
    val totalDataReceived: Int,
    val completedData: Int,
    val brokenData: Int,
    val duplicatedData: Int,
    val printData: Int,
    val offerData: Int,
    val idcardPrint: Int,
    val idcardOffer: Int
)

@Service
class Nis07A2MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_07a2"

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
            parseCsv07A2(inputStream, defaultYear)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv07A2(inputStream: InputStream, defaultYear: Int): List<Nis07A2NationalIdCardRecord> {
        val records = mutableListOf<Nis07A2NationalIdCardRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 14) return emptyList()

            // Header titles span rows 0..12; data rows start at index 13
            for (rowIndex in 13 until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 11) continue

                val proCode = row.getOrNull(0)?.trim() ?: ""
                val proName = row.getOrNull(1)?.trim() ?: ""

                // Skip non-data / total rows or repeated CSV column header labels
                if (proCode.isBlank() ||
                    proCode.equals("Pro_code", ignoreCase = true)
                ) continue

                records.add(
                    Nis07A2NationalIdCardRecord(
                        year = defaultYear,
                        provinceCode = proCode,
                        provinceName = proName,
                        planToProvide = parseKhmerInt(row.getOrNull(2)),
                        totalDataReceived = parseKhmerInt(row.getOrNull(3)),
                        completedData = parseKhmerInt(row.getOrNull(4)),
                        brokenData = parseKhmerInt(row.getOrNull(5)),
                        duplicatedData = parseKhmerInt(row.getOrNull(6)),
                        printData = parseKhmerInt(row.getOrNull(7)),
                        offerData = parseKhmerInt(row.getOrNull(8)),
                        idcardPrint = parseKhmerInt(row.getOrNull(9)),
                        idcardOffer = parseKhmerInt(row.getOrNull(10))
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

    private fun insertBatch(connection: Connection, records: List<Nis07A2NationalIdCardRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_07a2_national_id_card
            (year, province_code, province_name, plan_to_provide, total_data_received, completed_data, broken_data, duplicated_data, print_data, offer_data, idcard_print, idcard_offer, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, province_code, upload_id) DO UPDATE SET
                province_name = EXCLUDED.province_name,
                plan_to_provide = EXCLUDED.plan_to_provide,
                total_data_received = EXCLUDED.total_data_received,
                completed_data = EXCLUDED.completed_data,
                broken_data = EXCLUDED.broken_data,
                duplicated_data = EXCLUDED.duplicated_data,
                print_data = EXCLUDED.print_data,
                offer_data = EXCLUDED.offer_data,
                idcard_print = EXCLUDED.idcard_print,
                idcard_offer = EXCLUDED.idcard_offer
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setInt(1, r.year)
                stmt.setString(2, r.provinceCode)
                stmt.setString(3, r.provinceName)
                stmt.setInt(4, r.planToProvide)
                stmt.setInt(5, r.totalDataReceived)
                stmt.setInt(6, r.completedData)
                stmt.setInt(7, r.brokenData)
                stmt.setInt(8, r.duplicatedData)
                stmt.setInt(9, r.printData)
                stmt.setInt(10, r.offerData)
                stmt.setInt(11, r.idcardPrint)
                stmt.setInt(12, r.idcardOffer)
                stmt.setString(13, uploadId)
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