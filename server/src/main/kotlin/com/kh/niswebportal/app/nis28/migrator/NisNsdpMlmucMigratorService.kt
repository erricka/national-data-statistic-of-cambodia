package com.kh.niswebportal.app.nis28.migrator

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

data class NisNsdpMlmucRecord(
    val indicatorNo: String,
    val indicatorNameKh: String,
    val unit: String,
    val year: Int,
    val targetVal: Double = 0.0,
    val actualVal: Double = 0.0,
    val isAdditionalIndicator: Boolean = false
)

@Service
class NisNsdpMlmucMigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_nsdp_mlmuc"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for Tab_NSDP_MLMUC import." }

        val records = file.inputStream.use { inputStream ->
            parseCsvNsdpMlmuc(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsvNsdpMlmuc(inputStream: InputStream): List<NisNsdpMlmucRecord> {
        val records = mutableListOf<NisNsdpMlmucRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            // Locate header row for years
            val yearRowIdx = rows.indexOfFirst { r ->
                r.size() > 3 && r.any { cell -> cell.trim().matches(Regex("2\\d{3}")) }
            }

            if (yearRowIdx == -1 || yearRowIdx + 1 >= rows.size) return emptyList()

            val yearRow = rows[yearRowIdx]
            val yearColPairs = mutableListOf<Pair<Int, Int>>() // Pair(year, startColIndexForTarget)

            var currentCol = 3
            while (currentCol < yearRow.size()) {
                val cellVal = yearRow.getOrNull(currentCol)?.trim()?.toLatinDigits() ?: ""
                val yearMatch = Regex("2\\d{3}").find(cellVal)
                if (yearMatch != null) {
                    val year = yearMatch.value.toInt()
                    yearColPairs.add(Pair(year, currentCol))
                    currentCol += 2
                } else {
                    currentCol++
                }
            }

            var isAdditional = false

            for (rIdx in (yearRowIdx + 2) until rows.size) {
                val row = rows[rIdx]
                if (row.size() < 2) continue

                val noCol = row.getOrNull(0)?.trim() ?: ""
                val nameCol = row.getOrNull(1)?.trim() ?: ""
                val unitCol = row.getOrNull(2)?.trim() ?: ""

                if (nameCol.contains("សូចនាករបន្ថែម")) {
                    isAdditional = true
                    continue
                }

                if (noCol.isBlank() && nameCol.isBlank()) continue

                val indicatorNo = noCol.toLatinDigits()

                for ((year, targetCol) in yearColPairs) {
                    val targetStr = row.getOrNull(targetCol)
                    val actualStr = row.getOrNull(targetCol + 1)

                    val targetVal = parseKhmerDouble(targetStr)
                    val actualVal = parseKhmerDouble(actualStr)

                    if (targetStr.isNullOrBlank() && actualStr.isNullOrBlank()) continue

                    records.add(
                        NisNsdpMlmucRecord(
                            indicatorNo = indicatorNo,
                            indicatorNameKh = nameCol,
                            unit = unitCol,
                            year = year,
                            targetVal = targetVal,
                            actualVal = actualVal,
                            isAdditionalIndicator = isAdditional
                        )
                    )
                }
            }
        }
        return records
    }

    private fun parseKhmerDouble(text: String?): Double {
        if (text.isNullOrBlank()) return 0.0
        val trimmed = text.trim()

        if (trimmed == "-" || trimmed.contains("-")) return 0.0

        val normalized = trimmed.toLatinDigits()
        val cleanNumber = normalized.replace(",", "").replace(" ", "")
        return cleanNumber.toDoubleOrNull() ?: 0.0
    }

    private fun insertBatch(connection: Connection, records: List<NisNsdpMlmucRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_nsdp_mlmuc
            (indicator_no, indicator_name_kh, unit, year, target_val, actual_val, is_additional_indicator, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (indicator_no, year, upload_id) DO UPDATE SET
                indicator_name_kh = EXCLUDED.indicator_name_kh,
                unit = EXCLUDED.unit,
                target_val = EXCLUDED.target_val,
                actual_val = EXCLUDED.actual_val,
                is_additional_indicator = EXCLUDED.is_additional_indicator
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.indicatorNo)
                stmt.setString(2, r.indicatorNameKh)
                stmt.setString(3, r.unit)
                stmt.setInt(4, r.year)
                stmt.setDouble(5, r.targetVal)
                stmt.setDouble(6, r.actualVal)
                stmt.setBoolean(7, r.isAdditionalIndicator)
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

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}