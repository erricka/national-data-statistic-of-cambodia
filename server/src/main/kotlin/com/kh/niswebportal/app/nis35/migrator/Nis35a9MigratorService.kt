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

data class Nis35a9Record(
    val indicatorCode: String,
    val indicatorEn: String,
    val unit: String?,
    val year: String,
    val metricValue: String?
)

@Service
class Nis35a9MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_35a9"

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

    private fun parseMatrixData(rows: List<List<String>>): List<Nis35a9Record> {
        val records = mutableListOf<Nis35a9Record>()

        if (rows.size <= 4) return emptyList()

        // Extract unit from Row 3 (index 3)
        val extractedUnit = rows.getOrNull(3)?.firstOrNull { it.contains("Unit", ignoreCase = true) }?.trim()

        // Row 4 (index 4) contains period headers starting at column index 5 ("Jan-13", "Feb-13", etc.)
        val rowHeader = rows[4]
        val yearColMap = mutableMapOf<Int, String>()

        for (c in 5 until rowHeader.size) {
            val headerStr = rowHeader.getOrNull(c)?.trim() ?: ""
            if (headerStr.isNotBlank() && !headerStr.equals("nan", ignoreCase = true)) {
                yearColMap[c] = headerStr
            }
        }

        // Data rows start from Row 6 (index 6) to Row 54 (index 54)
        for (i in 6 until rows.size) {
            val row = rows[i]
            val indCode = row.getOrNull(0)?.trim() ?: ""

            // Combine column 1 and column 2 if indicator code exists
            val indTitle = row.getOrNull(1)?.trim() ?: ""
            val indSub = row.getOrNull(2)?.trim() ?: ""
            val indicatorEn = when {
                indTitle.isNotBlank() && indSub.isNotBlank() -> "$indTitle - $indSub"
                indTitle.isNotBlank() -> indTitle
                indSub.isNotBlank() -> indSub
                else -> ""
            }

            if (indCode.isBlank() || indCode.equals("nan", ignoreCase = true)) {
                continue
            }

            for ((c, yearPeriod) in yearColMap) {
                val rawVal = row.getOrNull(c)

                records.add(
                    Nis35a9Record(
                        indicatorCode = indCode,
                        indicatorEn = indicatorEn,
                        unit = extractedUnit,
                        year = yearPeriod,
                        metricValue = cleanValue(rawVal)
                    )
                )
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis35a9Record>, uploadId: String) {
        val sql = """
            INSERT INTO public.nis_35a9_deposit_money_banks_interest_rates
            (indicator_code, indicator_en, unit, year, metric_value, upload_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (indicator_code, year, upload_id) DO UPDATE SET
                indicator_en = EXCLUDED.indicator_en,
                unit = EXCLUDED.unit,
                metric_value = EXCLUDED.metric_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.indicatorCode)
                stmt.setString(2, r.indicatorEn)
                stmt.setString(3, r.unit)
                stmt.setString(4, r.year)
                stmt.setString(5, r.metricValue)
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

    private fun cleanValue(value: String?): String? {
        val cleaned = value?.trim()?.replace(" ", "")?.replace(",", "") ?: return null
        if (cleaned.isBlank() || cleaned == "-" || cleaned.equals("nan", ignoreCase = true) || cleaned.equals("n.a", ignoreCase = true)) return null
        return cleaned.toLatinDigits()
    }

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }
}