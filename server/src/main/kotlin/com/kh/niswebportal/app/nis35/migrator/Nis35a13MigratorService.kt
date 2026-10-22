package com.kh.niswebportal.app.nis35.migrator

import com.kh.niswebportal.core.strategy.NisMigratorStrategy
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.sql.Connection
import javax.sql.DataSource

data class Nis35a13Record(
    val year: String,
    val month: String,
    val unit: String?,
    val marketFxPurchase: String?,
    val marketFxSale: String?,
    val marketFxMidpoint: String?,
    val officialFxPurchase: String?,
    val officialFxSale: String?,
    val officialFxMidpoint: String?,
    val marketBuyingRateM2m: String?,
    val marketBuyingRateLastYear: String?
)

@Service
class Nis35a13MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_35a13"

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

        val rows: List<List<String>> = when {
            fileName.endsWith(".csv") -> {
                file.inputStream.use { parseCsv(it) }
            }
            fileName.endsWith(".xlsx") || fileName.endsWith(".xls") -> {
                file.inputStream.use { parseExcel(it) }
            }
            else -> throw IllegalArgumentException("Unsupported file format. Please upload CSV or Excel (.xls, .xlsx).")
        }

        val records = parseMatrixData(rows)

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv(inputStream: InputStream): List<List<String>> {
        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            return parser.records.map { record ->
                (0 until record.size()).map { i -> record.get(i) ?: "" }
            }
        }
    }

    private fun parseExcel(inputStream: InputStream): List<List<String>> {
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0)
        val formatter = DataFormatter()
        val matrix = mutableListOf<List<String>>()

        for (row in sheet) {
            val rowValues = mutableListOf<String>()
            val lastCellNum = row.lastCellNum.toInt()
            for (cellIdx in 0 until lastCellNum) {
                val cell = row.getCell(cellIdx)
                rowValues.add(formatter.formatCellValue(cell))
            }
            matrix.add(rowValues)
        }
        workbook.close()
        return matrix
    }

    private fun parseMatrixData(rows: List<List<String>>): List<Nis35a13Record> {
        val records = mutableListOf<Nis35a13Record>()

        if (rows.size <= 3) return emptyList()

        // Extract unit from Row 3 (index 3) e.g., "(Unit: Riel/U.S $)"
        val extractedUnit = rows.getOrNull(3)?.firstOrNull { it.contains("Unit", ignoreCase = true) }?.trim()

        var currentYear = ""

        for (i in 7 until rows.size) {
            val row = rows[i]
            val col0 = row.getOrNull(0)?.trim() ?: ""

            if (col0.isBlank() || col0.equals("nan", ignoreCase = true)) {
                continue
            }

            // Check if col0 represents a 4-digit year (e.g., "2020", "2021")
            if (col0.matches(Regex("\\d{4}"))) {
                currentYear = col0
                continue
            }

            // If we have a current year and col0 is a month/row name
            if (currentYear.isNotBlank()) {
                val month = col0
                records.add(
                    Nis35a13Record(
                        year = currentYear,
                        month = month,
                        unit = extractedUnit,
                        marketFxPurchase = cleanValue(row.getOrNull(1)),
                        marketFxSale = cleanValue(row.getOrNull(2)),
                        marketFxMidpoint = cleanValue(row.getOrNull(3)),
                        officialFxPurchase = cleanValue(row.getOrNull(4)),
                        officialFxSale = cleanValue(row.getOrNull(5)),
                        officialFxMidpoint = cleanValue(row.getOrNull(6)),
                        marketBuyingRateM2m = cleanValue(row.getOrNull(7)),
                        marketBuyingRateLastYear = cleanValue(row.getOrNull(8))
                    )
                )
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis35a13Record>, uploadId: String) {
        val sql = """
            INSERT INTO public.nis_35a13_monthly_average_exchange_rate
            (year, month, unit, market_fx_purchase, market_fx_sale, market_fx_midpoint, official_fx_purchase, official_fx_sale, official_fx_midpoint, market_buying_rate_m2m, market_buying_rate_last_year, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, month, upload_id) DO UPDATE SET
                unit = EXCLUDED.unit,
                market_fx_purchase = EXCLUDED.market_fx_purchase,
                market_fx_sale = EXCLUDED.market_fx_sale,
                market_fx_midpoint = EXCLUDED.market_fx_midpoint,
                official_fx_purchase = EXCLUDED.official_fx_purchase,
                official_fx_sale = EXCLUDED.official_fx_sale,
                official_fx_midpoint = EXCLUDED.official_fx_midpoint,
                market_buying_rate_m2m = EXCLUDED.market_buying_rate_m2m,
                market_buying_rate_last_year = EXCLUDED.market_buying_rate_last_year
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.year)
                stmt.setString(2, r.month)
                stmt.setString(3, r.unit)
                stmt.setString(4, r.marketFxPurchase)
                stmt.setString(5, r.marketFxSale)
                stmt.setString(6, r.marketFxMidpoint)
                stmt.setString(7, r.officialFxPurchase)
                stmt.setString(8, r.officialFxSale)
                stmt.setString(9, r.officialFxMidpoint)
                stmt.setString(10, r.marketBuyingRateM2m)
                stmt.setString(11, r.marketBuyingRateLastYear)
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
        if (cleaned.isBlank() || cleaned == "-" || cleaned.equals("nan", ignoreCase = true) || cleaned.equals("n.a", ignoreCase = true)) return null
        return cleaned.toLatinDigits()
    }

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }
}