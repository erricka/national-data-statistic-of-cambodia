package com.kh.niswebportal.app.nis90.migrator

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
import org.slf4j.LoggerFactory

data class B1a3Record(
    val sourceId: String,
    val indicatorCode: String,
    val unit: String,
    val dataDate: String,
    val metricValue: String
)

@Service
class B1a3MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "b1a3"
    private val logger = LoggerFactory.getLogger(javaClass)

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

    private fun extractDateString(internalCode: String): String {
        // e.g., b_1_main_us_pro_2019_Jan -> 2019_Jan
        // e.g., b_1_market_un_pro_2019_Feb -> 2019_Feb
        val parts = internalCode.split("_")
        if (parts.size >= 2) {
            val yr = parts[parts.size - 2]
            val mo = parts[parts.size - 1]
            if (yr.length == 4) return yr + "_" + mo
        }
        // Fallback if the code doesn't exactly match the pattern
        return internalCode.takeLast(8) 
    }

    private fun parseMatrixData(rows: List<List<String>>): List<B1a3Record> {
        val records = mutableListOf<B1a3Record>()
        
        // Find internal code header row (Row generally around index 3: b_1_main_us_pro_xyz)
        var codeRowIndex = -1
        for (i in 0 until java.lang.Math.min(rows.size, 10)) {
            if (rows[i].any { it.startsWith("b_1_", ignoreCase = true) || it.contains("_2019_") || it.contains("_202") }) {
                codeRowIndex = i
                break
            }
        }
        
        if (codeRowIndex == -1) return emptyList()
        val headerRow = rows[codeRowIndex]

        // Find if this dataset has a Unit column ("Unit" or "unit" in the 2nd col usually index 1)
        var hasUnitColumn = false
        var unitColIndex = -1
        for (i in 0 until codeRowIndex) {
            val r = rows[i]
            if (r.size > 1 && r[1].trim().equals("Unit", ignoreCase = true)) {
                hasUnitColumn = true
                unitColIndex = 1
                break
            }
        }
        
        var dateColsStart = 1
        for (c in 1 until headerRow.size) {
            val hc = headerRow.getOrNull(c)?.trim() ?: ""
            if (hc.contains("_20")) {
                dateColsStart = c
                break
            }
        }

        for (i in (codeRowIndex + 1) until rows.size) {
            val row = rows[i]
            if (row.isEmpty()) continue
            
            val indCode = row.getOrNull(0)?.trim() ?: ""
            if (indCode.isBlank() || indCode.equals("nan", ignoreCase = true)) continue

            val unit = if (hasUnitColumn) row.getOrNull(unitColIndex)?.trim() ?: "" else ""

            for (c in dateColsStart until java.lang.Math.min(row.size, headerRow.size)) {
                val internalCode = headerRow.getOrNull(c)?.trim() ?: ""
                if (internalCode.isBlank() || internalCode.equals("nan", ignoreCase = true)) continue

                val dataDate = extractDateString(internalCode)
                
                val rawVal = row.getOrNull(c)?.trim()?.replace(",", "") ?: ""
                val isValueValid = rawVal.isNotBlank() && (!rawVal.equals("nan", ignoreCase = true) && !rawVal.equals("0"))
                
                if (isValueValid) {
                   records.add(
                       B1a3Record(
                           sourceId = "b1a3",
                           indicatorCode = indCode,
                           unit = unit,
                           dataDate = dataDate, // e.g. 2019_Jan
                           metricValue = rawVal
                       )
                   )
                }
            }
        }
                return records
    }

    private fun insertBatch(connection: Connection, records: List<B1a3Record>, uploadId: String) {
        val sql = """
            INSERT INTO public.nis_90_stats
            (source_id, indicator_code, unit, data_date, metric_value, upload_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (source_id, indicator_code, data_date, upload_id) DO UPDATE SET
                unit = EXCLUDED.unit,
                metric_value = EXCLUDED.metric_value
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.sourceId)
                stmt.setString(2, r.indicatorCode)
                stmt.setString(3, r.unit)
                stmt.setString(4, r.dataDate)
                stmt.setString(5, r.metricValue)
                stmt.setString(6, uploadId)
                stmt.addBatch()

                if (++count % BATCH_SIZE == 0) stmt.executeBatch()
            }
            if (count % BATCH_SIZE != 0) stmt.executeBatch()
        }
    }
}
