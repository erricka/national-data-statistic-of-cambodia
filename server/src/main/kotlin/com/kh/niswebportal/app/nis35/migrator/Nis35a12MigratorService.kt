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

data class Nis35a12Record(
    val period: String,
    val numAtmTerminal: String?,
    val numPos: String?,
    val numDebitCard: String?,
    val numCreditCard: String?
)

@Service
class Nis35a12MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_35a12"

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

    private fun parseMatrixData(rows: List<List<String>>): List<Nis35a12Record> {
        val records = mutableListOf<Nis35a12Record>()

        if (rows.size <= 6) return emptyList()

        // Iterate starting from row index 6 onwards (skipping metadata, header rows, and row 54 mapping labels row)
        for (i in 6 until rows.size) {
            val row = rows[i]
            val period = row.getOrNull(0)?.trim() ?: ""

            if (period.isBlank() || period.equals("nan", ignoreCase = true) || period.startsWith("*")) {
                continue
            }

            val atm = cleanValue(row.getOrNull(1))
            val pos = cleanValue(row.getOrNull(2))
            val debit = cleanValue(row.getOrNull(3))
            val credit = cleanValue(row.getOrNull(4))

            records.add(
                Nis35a12Record(
                    period = period,
                    numAtmTerminal = atm,
                    numPos = pos,
                    numDebitCard = debit,
                    numCreditCard = credit
                )
            )
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis35a12Record>, uploadId: String) {
        val sql = """
            INSERT INTO public.nis_35a12_atm_pos_cards
            (period, num_atm_terminal, num_pos, num_debit_card, num_credit_card, upload_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (period, upload_id) DO UPDATE SET
                num_atm_terminal = EXCLUDED.num_atm_terminal,
                num_pos = EXCLUDED.num_pos,
                num_debit_card = EXCLUDED.num_debit_card,
                num_credit_card = EXCLUDED.num_credit_card
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.period)
                stmt.setString(2, r.numAtmTerminal)
                stmt.setString(3, r.numPos)
                stmt.setString(4, r.numDebitCard)
                stmt.setString(5, r.numCreditCard)
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