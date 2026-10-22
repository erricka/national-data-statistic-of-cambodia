package com.kh.niswebportal.app.nis18.migrator

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

data class Nis18a8Record(
    val provinceCode: String,
    val provinceKh: String,
    val year: String,
    val templeFoundationCount: String?,
    val burialMoundCount: String?,
    val residentialMoundCount: String?,
    val irrigationSystemCount: String?,
    val otherCount: String?
)

@Service
class Nis18a8MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_18a8"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 18 A8 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv18a8(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv18a8(inputStream: InputStream): List<Nis18a8Record> {
        val records = mutableListOf<Nis18a8Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 11) return emptyList()

            val rowYears = rows[8]
            val yearColTuples = mutableListOf<Triple<String, Int, List<Int>>>()

            var c = 3
            while (c < rowYears.size()) {
                val yearHeader = rowYears.getOrNull(c)?.trim() ?: ""
                val yearMatch = Regex("\\d{4}").find(yearHeader.toLatinDigits())?.value
                if (yearMatch != null) {
                    val subCols = listOf(c, c + 1, c + 2, c + 3, c + 4)
                    yearColTuples.add(Triple(yearMatch, c, subCols))
                    c += 5
                } else {
                    c++
                }
            }

            for (i in 11 until rows.size) {
                val row = rows[i]

                val provCode = row.getOrNull(1)?.trim() ?: ""
                val provKh = row.getOrNull(2)?.trim() ?: ""

                if (provKh.isBlank() || provKh == "សរុប") {
                    continue
                }

                val effectiveProvCode = if (provCode.isBlank() || provCode == "18A8_") {
                    "18A8_PROV_${i - 10}"
                } else {
                    provCode
                }

                for ((year, _, subCols) in yearColTuples) {
                    val templeVal = cleanValue(row.getOrNull(subCols[0]))
                    val burialVal = cleanValue(row.getOrNull(subCols[1]))
                    val resVal = cleanValue(row.getOrNull(subCols[2]))
                    val irrigVal = cleanValue(row.getOrNull(subCols[3]))
                    val othVal = cleanValue(row.getOrNull(subCols[4]))

                    records.add(
                        Nis18a8Record(
                            provinceCode = effectiveProvCode,
                            provinceKh = provKh,
                            year = year,
                            templeFoundationCount = templeVal,
                            burialMoundCount = burialVal,
                            residentialMoundCount = resVal,
                            irrigationSystemCount = irrigVal,
                            otherCount = othVal
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis18a8Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_18a8_ancient_site_stats
            (province_code, province_kh, year, temple_foundation_count, burial_mound_count, residential_mound_count, irrigation_system_count, other_count, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (province_code, year, upload_id) DO UPDATE SET
                province_kh = EXCLUDED.province_kh,
                temple_foundation_count = EXCLUDED.temple_foundation_count,
                burial_mound_count = EXCLUDED.burial_mound_count,
                residential_mound_count = EXCLUDED.residential_mound_count,
                irrigation_system_count = EXCLUDED.irrigation_system_count,
                other_count = EXCLUDED.other_count
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.provinceCode)
                stmt.setString(2, r.provinceKh)
                stmt.setString(3, r.year)
                stmt.setString(4, r.templeFoundationCount)
                stmt.setString(5, r.burialMoundCount)
                stmt.setString(6, r.residentialMoundCount)
                stmt.setString(7, r.irrigationSystemCount)
                stmt.setString(8, r.otherCount)
                stmt.setString(9, uploadId)
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