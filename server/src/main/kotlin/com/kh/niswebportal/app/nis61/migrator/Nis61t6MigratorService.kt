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

data class Nis61t6Record(
    val itemCode: String,
    val sector: String,
    val year2025Value: String?,
    val year2026Value: String?,
    val occupation: String?,
    val occupationValue: String?
)

@Service
class Nis61t6MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_61t6"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 61 T6 import." }

        val records = mutableListOf<Nis61t6Record>()

        file.inputStream.use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val parser = CSVParser(reader, CSVFormat.DEFAULT)
                val rows = parser.records

                // Rows 2 through 10 contain the structured rows of labor statistics and occupations
                for (i in 2 until rows.size) {
                    val row = rows[i]
                    val codeCol = row.getOrNull(0)?.trim() ?: ""
                    val sectorCol = row.getOrNull(1)?.trim() ?: ""

                    if (codeCol.isBlank() && sectorCol.isBlank()) {
                        continue
                    }

                    val record = Nis61t6Record(
                        itemCode = codeCol,
                        sector = sectorCol,
                        year2025Value = cleanValue(row.getOrNull(2)),
                        year2026Value = cleanValue(row.getOrNull(3)),
                        occupation = cleanValue(row.getOrNull(4)),
                        occupationValue = cleanValue(row.getOrNull(5))
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

    private fun insertBatch(connection: Connection, records: List<Nis61t6Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_61t6_labor_market_status
            (item_code, sector, year_2025_value, year_2026_value, occupation, occupation_value, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.itemCode)
                stmt.setString(2, r.sector)
                stmt.setString(3, r.year2025Value)
                stmt.setString(4, r.year2026Value)
                stmt.setString(5, r.occupation)
                stmt.setString(6, r.occupationValue)
                stmt.setString(7, uploadId)
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