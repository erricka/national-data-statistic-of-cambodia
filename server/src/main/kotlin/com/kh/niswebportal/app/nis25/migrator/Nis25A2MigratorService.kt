package com.kh.niswebportal.app.nis25.migrator

import com.kh.niswebportal.core.strategy.NisMigratorStrategy
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.io.InputStreamReader
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.sql.Connection
import javax.sql.DataSource

data class Nis25A2RoadCategoryRecord(
    val indicatorCode: String,
    val indicatorNameKh: String,
    val roadTypeCode: String,
    val roadTypeNameKh: String,
    val year: String,
    val metricValue: BigDecimal?
)

@Service
class Nis25A2MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_25a2"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 25A2 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv25A2(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv25A2(inputStream: InputStream): List<Nis25A2RoadCategoryRecord> {
        val records = mutableListOf<Nis25A2RoadCategoryRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 8) return emptyList()

            val rowYear = rows[6]      // Year header
            val rowTypeName = rows[7]  // Khmer category name header
            val rowTypeCode = rows[8]  // Category code header

            // Build dynamic metadata per column index (Col 3 to Col 26)
            val columnMeta = mutableMapOf<Int, Triple<String, String, String>>() // colIdx -> (Year, TypeCode, TypeNameKh)

            var currentYear = ""
            for (colIdx in 3 until rowYear.size()) {
                val rawYear = rowYear.getOrNull(colIdx)?.toLatinDigits()?.trim()
                if (!rawYear.isNullOrBlank()) {
                    currentYear = rawYear.replace(".0", "").trim()
                }

                val typeNameKh = rowTypeName.getOrNull(colIdx)?.trim() ?: ""
                var typeCode = rowTypeCode.getOrNull(colIdx)?.trim() ?: ""

                if (typeCode.isBlank()) {
                    typeCode = if (typeNameKh.contains("សរុប")) "25a2_total" else "25a2_unknown"
                }

                if (currentYear.isNotBlank() && typeNameKh.isNotBlank()) {
                    columnMeta[colIdx] = Triple(currentYear, typeCode, typeNameKh)
                }
            }

            // Read data rows starting from index 9
            for (i in 9 until rows.size) {
                val row = rows[i]
                val code = row.getOrNull(0)?.trim() ?: ""

                if (code.startsWith("25a2_")) {
                    val indicatorNameKh = row.getOrNull(1)?.trim() ?: ""

                    for ((colIdx, meta) in columnMeta) {
                        val (year, typeCode, typeNameKh) = meta
                        val rawVal = row.getOrNull(colIdx)?.trim()?.replace(",", "")

                        val metricValue = if (rawVal.isNullOrBlank() || rawVal == "-") {
                            null
                        } else {
                            rawVal.toLatinDigits().toBigDecimalOrNull()
                        }

                        records.add(
                            Nis25A2RoadCategoryRecord(
                                indicatorCode = code,
                                indicatorNameKh = indicatorNameKh,
                                roadTypeCode = typeCode,
                                roadTypeNameKh = typeNameKh,
                                year = year,
                                metricValue = metricValue
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis25A2RoadCategoryRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_25a2_national_provincial_roads_stats
            (indicator_code, indicator_name_kh, road_type_code, road_type_name_kh, year, metric_value, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (indicator_code, road_type_code, year, upload_id) DO UPDATE SET
                indicator_name_kh = EXCLUDED.indicator_name_kh,
                road_type_name_kh = EXCLUDED.road_type_name_kh,
                metric_value = EXCLUDED.metric_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.indicatorCode)
                stmt.setString(2, r.indicatorNameKh)
                stmt.setString(3, r.roadTypeCode)
                stmt.setString(4, r.roadTypeNameKh)
                stmt.setString(5, r.year)
                if (r.metricValue != null) {
                    stmt.setBigDecimal(6, r.metricValue)
                } else {
                    stmt.setNull(6, java.sql.Types.NUMERIC)
                }
                stmt.setString(7, uploadId)
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