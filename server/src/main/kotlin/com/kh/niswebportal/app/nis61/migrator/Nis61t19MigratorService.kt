package com.kh.niswebportal.app.nis61.migrator

import com.kh.niswebportal.core.strategy.NisMigratorStrategy
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.sql.Connection
import javax.sql.DataSource

data class Nis61t19Record(
    val itemCode: String?,
    val description: String,
    val total2025: String?,
    val female2025: String?,
    val total2026: String?,
    val female2026: String?,
    val totalCumulative: String?,
    val femaleCumulative: String?
)

@Service
class Nis61t19MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_61t19"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 61 T19 import." }

        val records = mutableListOf<Nis61t19Record>()

        file.inputStream.use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val parser = CSVParser(reader, CSVFormat.DEFAULT)
                val rows = parser.records

                // Rows 3 through 9 contain the registered engineers statistics
                for (i in 3 until rows.size) {
                    val row = rows[i]
                    val codeCol = row.getOrNull(0)?.trim()
                    val descCol = row.getOrNull(1)?.trim() ?: ""

                    if (descCol.isBlank() || descCol.lowercase() == "nan") {
                        continue
                    }

                    val record = Nis61t19Record(
                        itemCode = if (codeCol?.lowercase() == "nan" || codeCol.isNullOrBlank()) null else codeCol,
                        description = descCol,
                        total2025 = cleanValue(row.getOrNull(2)),
                        female2025 = cleanValue(row.getOrNull(3)),
                        total2026 = cleanValue(row.getOrNull(4)),
                        female2026 = cleanValue(row.getOrNull(5)),
                        totalCumulative = cleanValue(row.getOrNull(6)),
                        femaleCumulative = cleanValue(row.getOrNull(7))
                    )

                    records.add(record)
                }

                if (records.isNotEmpty()) {
                    dataSource.connection.use { connection ->
                        insertBatch(connection, records, uploadId)
                    }
                }
            }
        }

        return records.size
    }

    private fun insertBatch(connection: Connection, records: List<Nis61t19Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_61t19_registered_engineers
            (item_code, description, total_2025, female_2025, total_2026, 
             female_2026, total_cumulative, female_cumulative, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.itemCode)
                stmt.setString(2, r.description)
                stmt.setString(3, r.total2025)
                stmt.setString(4, r.female2025)
                stmt.setString(5, r.total2026)
                stmt.setString(6, r.female2026)
                stmt.setString(7, r.totalCumulative)
                stmt.setString(8, r.femaleCumulative)
                stmt.setString(9, uploadId)
                stmt.addBatch()

                count++
                if (count % BATCH_SIZE == 0) stmt.executeBatch()
            }
            if (count % BATCH_SIZE != 0) stmt.executeBatch()
        }
    }

    private fun cleanValue(value: String?): String? {
        val cleaned = value?.trim()?.replace(",", "") ?: return null
        if (cleaned.isBlank() || cleaned == "-" || cleaned == "nan" || cleaned == " - ") return null
        return cleaned
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}