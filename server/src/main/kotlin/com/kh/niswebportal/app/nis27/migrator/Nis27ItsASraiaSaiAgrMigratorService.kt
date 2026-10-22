package com.kh.niswebportal.app.nis27.migrator

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

data class Nis27ItsASraiaSaiAgrRecord(
    val sourceId: String,
    val indicatorCode: String,
    val indicatorName: String,
    val year: String,
    val metricValue: String
)

@Service
class Nis27ItsASraiaSaiAgrMigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_27_its_a_sraia_sai_agr"

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

    private fun parseMatrixData(rows: List<List<String>>): List<Nis27ItsASraiaSaiAgrRecord> {
        val records = mutableListOf<Nis27ItsASraiaSaiAgrRecord>()
        
        var yearRowIndex = -1
        for (i in 0 until java.lang.Math.min(rows.size, 10)) {
            val hasYears = rows[i].any { it.replace(",", "").contains("2016") || it.replace(",", "").contains("2008") || it.replace(",", "").contains("2010") }
            if (hasYears) {
                yearRowIndex = i
                break
            }
        }
        
        if (yearRowIndex == -1) return emptyList()
        val headerRow = rows[yearRowIndex]

        for (i in (yearRowIndex + 1) until rows.size) {
            val row = rows[i]
            if (row.isEmpty()) continue
            
            val indCode = row.getOrNull(0)?.trim() ?: ""
            val indName = row.getOrNull(1)?.trim() ?: ""
            if (indCode.isBlank() || indCode.equals("nan", ignoreCase = true)) continue

            for (c in 2 until java.lang.Math.min(row.size, headerRow.size)) {
                var year = headerRow.getOrNull(c)?.trim()?.replace(",", "") ?: ""
                if (year.contains(".")) year = year.substringBefore(".")
                year = year.replace(Regex("[^0-9]"), "")
                
                val rawVal = row.getOrNull(c)?.trim()?.replace(",", "") ?: ""
                
                val isYearValid = year.isNotBlank() && year.length == 4 && year.startsWith("20")
                val isValueValid = rawVal.isNotBlank() && !rawVal.equals("nan", ignoreCase = true)
                
                if (isYearValid && isValueValid) {
                   records.add(
                       Nis27ItsASraiaSaiAgrRecord(
                           sourceId = "nis_27_its_a_sraia_sai_agr",
                           indicatorCode = indCode,
                           indicatorName = indName,
                           year = year,
                           metricValue = rawVal
                       )
                   )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis27ItsASraiaSaiAgrRecord>, uploadId: String) {
        val sql = """
            INSERT INTO public.nis_27_stats
            (source_id, indicator_code, indicator_name, year, metric_value, upload_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (source_id, indicator_code, year, upload_id) DO UPDATE SET
                indicator_name = EXCLUDED.indicator_name,
                metric_value = EXCLUDED.metric_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.sourceId)
                stmt.setString(2, r.indicatorCode)
                stmt.setString(3, r.indicatorName)
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
