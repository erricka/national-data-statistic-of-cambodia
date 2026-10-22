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
import java.nio.charset.StandardCharsets
import java.sql.Connection
import javax.sql.DataSource

data class Nis25A11FreightRecord(
    val year: String,
    val indicatorCode: String,
    val routeNameKh: String,
    val tradeTypeNameKh: String,
    val freightVolumeTons: Double?
)

@Service
class Nis25A11MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_25a11"

    private companion object {
        private const val BATCH_SIZE = 1000
        private val KHMER_TO_LATIN_MAP = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
        private val TRADE_SUFFIX_MAP = mapOf(
            "ទំនិញនាំចេញ" to "exp",
            "ទំនិញនាំចូល" to "imp",
            "ទំនិញក្នុងស្រុក" to "dom",
            "សរុប" to "total"
        )
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 25A11 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv25A11(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv25A11(inputStream: InputStream): List<Nis25A11FreightRecord> {
        val records = mutableListOf<Nis25A11FreightRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 10) return emptyList()

            val rowRouteName = rows[6]
            val rowRouteCode = rows[7]
            val rowTradeName = rows[8]
            val rowIndicatorCode = rows[9]

            // Fill forward route headers across columns 2..13
            val routeNames = mutableMapOf<Int, String>()
            val routeCodes = mutableMapOf<Int, String>()
            var currentRouteName = ""
            var currentRouteCode = ""

            for (c in 2..13) {
                val rName = rowRouteName.getOrNull(c)?.trim() ?: ""
                val rCode = rowRouteCode.getOrNull(c)?.trim() ?: ""

                if (rName.isNotBlank() && rName != "nan") {
                    currentRouteName = rName
                }
                if (rCode.isNotBlank() && rCode != "nan") {
                    currentRouteCode = rCode
                }

                routeNames[c] = currentRouteName
                routeCodes[c] = currentRouteCode
            }

            // Column metadata map: colIdx -> (indicatorCode, routeNameKh, tradeTypeNameKh)
            val colMetaMap = mutableMapOf<Int, Triple<String, String, String>>()

            for (c in 2..13) {
                val rCode = routeCodes[c] ?: ""
                val rName = routeNames[c] ?: ""
                val tradeName = rowTradeName.getOrNull(c)?.trim() ?: ""
                val csvCode = rowIndicatorCode.getOrNull(c)?.trim() ?: ""

                val suffix = TRADE_SUFFIX_MAP[tradeName]
                val computedCode = if (rCode.isNotBlank() && suffix != null) {
                    "${rCode}_${suffix}"
                } else {
                    csvCode
                }

                if (computedCode.isNotBlank() && rName.isNotBlank()) {
                    colMetaMap[c] = Triple(computedCode, rName, tradeName)
                }
            }

            // Parse annual freight data starting at row index 10
            for (i in 10 until rows.size) {
                val row = rows[i]
                val yearRaw = row.getOrNull(0)?.toLatinDigits()?.trim() ?: ""

                if (yearRaw.matches(Regex("^\\d{4}$"))) {
                    for ((c, meta) in colMetaMap) {
                        val (indicatorCode, routeNameKh, tradeTypeNameKh) = meta
                        val rawVal = row.getOrNull(c)?.trim()?.replace(",", "")

                        val freightVal = if (rawVal.isNullOrBlank() || rawVal == "-" || rawVal == "nan") {
                            null
                        } else {
                            rawVal.toLatinDigits().toDoubleOrNull()
                        }

                        records.add(
                            Nis25A11FreightRecord(
                                year = yearRaw,
                                indicatorCode = indicatorCode,
                                routeNameKh = routeNameKh,
                                tradeTypeNameKh = tradeTypeNameKh,
                                freightVolumeTons = freightVal
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis25A11FreightRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_25a11_railway_freight_stats
            (year, indicator_code, route_name_kh, trade_type_name_kh, freight_volume_tons, upload_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, indicator_code, upload_id) DO UPDATE SET
                route_name_kh = EXCLUDED.route_name_kh,
                trade_type_name_kh = EXCLUDED.trade_type_name_kh,
                freight_volume_tons = EXCLUDED.freight_volume_tons
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.year)
                stmt.setString(2, r.indicatorCode)
                stmt.setString(3, r.routeNameKh)
                stmt.setString(4, r.tradeTypeNameKh)
                if (r.freightVolumeTons != null) {
                    stmt.setDouble(5, r.freightVolumeTons)
                } else {
                    stmt.setNull(5, java.sql.Types.NUMERIC)
                }
                stmt.setString(6, uploadId)
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