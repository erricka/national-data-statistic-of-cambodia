package com.kh.niswebportal.app.nis19.migrator

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

data class Nis19a4Record(
    val provinceCode: String,
    val provinceName: String,
    val environmentalOffenses: String?,
    val naturalResourceOffenses: String?
)

@Service
class Nis19a4MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_19a4"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 19 A4 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv19a4(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv19a4(inputStream: InputStream): List<Nis19a4Record> {
        val records = mutableListOf<Nis19a4Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 11) return emptyList()

            for (i in 12 until rows.size) {
                val row = rows[i]

                val codeRaw = row.getOrNull(0)?.trim() ?: ""
                val nameRaw = row.getOrNull(1)?.trim() ?: ""

                if (codeRaw.equals("Pro_Code", ignoreCase = true) || nameRaw.equals("Pro_name", ignoreCase = true)) {
                    continue
                }

                val effectiveCode = when {
                    codeRaw.isNotBlank() -> codeRaw.toLatinDigits()
                    codeRaw.contains("សរុប") || nameRaw.contains("សរុប") -> "TOTAL"
                    else -> continue
                }

                val effectiveName = if (nameRaw.isNotBlank()) nameRaw else if (effectiveCode == "TOTAL") "សរុប" else continue

                val envOffensesVal = cleanValue(row.getOrNull(2))
                val natOffensesVal = cleanValue(row.getOrNull(3))

                records.add(
                    Nis19a4Record(
                        provinceCode = effectiveCode,
                        provinceName = effectiveName,
                        environmentalOffenses = envOffensesVal,
                        naturalResourceOffenses = natOffensesVal
                    )
                )
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis19a4Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_19a4_environmental_offenses_stats
            (province_code, province_name, environmental_offenses, natural_resource_offenses, upload_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (province_code, upload_id) DO UPDATE SET
                province_name = EXCLUDED.province_name,
                environmental_offenses = EXCLUDED.environmental_offenses,
                natural_resource_offenses = EXCLUDED.natural_resource_offenses
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.provinceCode)
                stmt.setString(2, r.provinceName)
                stmt.setString(3, r.environmentalOffenses)
                stmt.setString(4, r.naturalResourceOffenses)
                stmt.setString(5, uploadId)
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
        if (cleaned.isBlank() || cleaned == "-" || cleaned == "nan") return null
        return cleaned.toLatinDigits()
    }

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}