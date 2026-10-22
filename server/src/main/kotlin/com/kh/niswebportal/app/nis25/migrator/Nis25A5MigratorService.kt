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

data class Nis25A5PortCargoRecord(
    val year: String,
    val tradeType: String,
    val tradeTypeKh: String,
    val cargoTypeCode: String,
    val cargoTypeNameKh: String,
    val metricValueThousandTons: BigDecimal?
)

@Service
class Nis25A5MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_25a5"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 25A5 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv25A5(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv25A5(inputStream: InputStream): List<Nis25A5PortCargoRecord> {
        val records = mutableListOf<Nis25A5PortCargoRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 9) return emptyList()

            val rowTradeName = rows[6]
            val rowCargoName = rows[8]
            val rowCargoCode = rows[9]

            // Column metadata map: colIdx -> (TradeType, TradeTypeKh, CargoTypeCode, CargoTypeNameKh)
            val colMetaMap = mutableMapOf<Int, Quadruple<String, String, String, String>>()

            var currentTradeKh = ""
            var currentTradeEn = ""

            for (c in 2..13) {
                val tradeKhRaw = rowTradeName.getOrNull(c)?.trim()
                if (!tradeKhRaw.isNullOrBlank()) {
                    currentTradeKh = tradeKhRaw
                    currentTradeEn = when {
                        tradeKhRaw.contains("សរុប") -> "TOTAL"
                        tradeKhRaw.contains("នាំចូល") -> "IMPORT"
                        tradeKhRaw.contains("នាំចេញ") -> "EXPORT"
                        else -> "TOTAL"
                    }
                }

                val cargoNameKh = rowCargoName.getOrNull(c)?.trim() ?: ""
                val cargoCode = rowCargoCode.getOrNull(c)?.trim() ?: ""

                if (currentTradeKh.isNotBlank() && cargoNameKh.isNotBlank()) {
                    colMetaMap[c] = Quadruple(currentTradeEn, currentTradeKh, cargoCode, cargoNameKh)
                }
            }

            // Iterate through yearly data rows (from row 10 onwards)
            for (i in 10 until rows.size) {
                val row = rows[i]
                val yearRaw = row.getOrNull(0)?.toLatinDigits()?.trim() ?: ""

                if (yearRaw.matches(Regex("^\\d{4}$"))) {
                    for ((c, meta) in colMetaMap) {
                        val (tradeEn, tradeKh, cargoCode, cargoNameKh) = meta
                        val rawVal = row.getOrNull(c)?.trim()?.replace(",", "")

                        val metricVal = if (rawVal.isNullOrBlank() || rawVal == "-") {
                            null
                        } else {
                            rawVal.toLatinDigits().toBigDecimalOrNull()
                        }

                        records.add(
                            Nis25A5PortCargoRecord(
                                year = yearRaw,
                                tradeType = tradeEn,
                                tradeTypeKh = tradeKh,
                                cargoTypeCode = cargoCode,
                                cargoTypeNameKh = cargoNameKh,
                                metricValueThousandTons = metricVal
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis25A5PortCargoRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_25a5_phnom_penh_port_cargo_stats
            (year, trade_type, trade_type_kh, cargo_type_code, cargo_type_name_kh, metric_value_thousand_tons, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, trade_type, cargo_type_code, upload_id) DO UPDATE SET
                trade_type_kh = EXCLUDED.trade_type_kh,
                cargo_type_name_kh = EXCLUDED.cargo_type_name_kh,
                metric_value_thousand_tons = EXCLUDED.metric_value_thousand_tons
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.year)
                stmt.setString(2, r.tradeType)
                stmt.setString(3, r.tradeTypeKh)
                stmt.setString(4, r.cargoTypeCode)
                stmt.setString(5, r.cargoTypeNameKh)
                if (r.metricValueThousandTons != null) {
                    stmt.setBigDecimal(6, r.metricValueThousandTons)
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

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
}