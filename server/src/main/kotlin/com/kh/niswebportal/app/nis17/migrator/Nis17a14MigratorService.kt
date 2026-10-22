package com.kh.niswebportal.app.nis17.migrator

import com.kh.niswebportal.core.strategy.NisMigratorStrategy
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.sql.Connection
import javax.sql.DataSource

data class Nis17a14Record(
    val indicatorCode: String,
    val indicatorKh: String,
    val unit: String?,
    val year: String,
    val metricValue: String?
)

@Service
class Nis17a14MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_17a14"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val rows = file.inputStream.use { parseCsv(it) }
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

    private fun parseMatrixData(rows: List<List<String>>): List<Nis17a14Record> {
        val records = mutableListOf<Nis17a14Record>()
        if (rows.size < 3) return emptyList()

        // 1. Find the year header row
        var yearRowIndex = -1
        for (i in 0 until java.lang.Math.min(rows.size, 6)) {
            val hasYears = rows[i].any { it.replace(",", "").contains("2008") || it.replace(",", "").contains("2009") || it.replace(",", "").contains("2010") }
            if (hasYears) {
                yearRowIndex = i
                break
            }
        }
        
        if ("Nis17a14" == "Nis17a1" && yearRowIndex == -1) yearRowIndex = 4
        if (yearRowIndex == -1) return emptyList()
        val headerRow = rows[yearRowIndex]
        
        // 2. Extract Unit
        var unitVal = ""
        for (i in 0..1) {
            val titleRow = rows.getOrNull(i) ?: continue
            val txt = titleRow.joinToString(" ")
            if (txt.contains("ពាន់ហិកតា")) unitVal = "ពាន់ហិកតា"
            else if (txt.contains("ពាន់តោន")) unitVal = "ពាន់តោន"
            else if (txt.contains("តោន/ហិកតា")) unitVal = "តោន/ហិកតា"
            else if (txt.contains("ពាន់ក្បាល")) unitVal = "ពាន់ក្បាល"
        }

        // 3. Process Data
        for (i in (yearRowIndex + 1) until rows.size) {
            val row = rows[i]
            if (row.isEmpty()) continue
            
            val indKh = row.getOrNull(0)?.trim() ?: ""
            if (indKh.isBlank() || indKh.equals("nan", ignoreCase = true) || indKh.equals("Total", ignoreCase = true)) continue

            for (c in 1 until java.lang.Math.min(row.size, headerRow.size)) {
                var year = headerRow.getOrNull(c)?.trim()?.replace(",", "") ?: ""
                if (year.contains(".")) year = year.substringBefore(".")
year = year.replace(Regex("[^0-9]"), "")
                
                val rawVal = row.getOrNull(c)?.trim()?.replace(",", "") ?: ""
                
                val isYearValid = year.isNotBlank() && year.length == 4 && year.startsWith("20")
                val isValueValid = rawVal.isNotBlank() && !rawVal.equals("nan", ignoreCase = true)
                
                if (isYearValid && isValueValid) {
                   records.add(
                       Nis17a14Record(
                           indicatorCode = indKh,
                           indicatorKh = indKh,
                           unit = unitVal,
                           year = year,
                           metricValue = rawVal
                       )
                   )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis17a14Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_17a14_stats
            (indicator_code, indicator_kh, unit, year, metric_value, upload_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (indicator_code, year, upload_id) DO UPDATE SET
                indicator_kh = EXCLUDED.indicator_kh,
                unit = EXCLUDED.unit,
                metric_value = EXCLUDED.metric_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.indicatorCode)
                stmt.setString(2, r.indicatorKh)
                stmt.setString(3, r.unit)
                stmt.setString(4, r.year)
                stmt.setString(5, r.metricValue)
                stmt.setString(6, uploadId)
                stmt.addBatch()

                if (++count % BATCH_SIZE == 0) stmt.executeBatch()
            }
            if (count % BATCH_SIZE != 0) stmt.executeBatch()
        }
    }
}
