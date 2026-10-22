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

data class Nis61t8Record(
    val itemCode: String?,
    val sector: String,
    val enterprise2025: String?,
    val totalEmployee2025: String?,
    val femaleEmployee2025: String?,
    val enterprise2026: String?,
    val totalEmployee2026: String?,
    val femaleEmployee2026: String?
)

@Service
class Nis61t8MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_61t8"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 61 T8 import." }

        val records = mutableListOf<Nis61t8Record>()

        file.inputStream.use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val parser = CSVParser(reader, CSVFormat.DEFAULT)
                val rows = parser.records

                // Rows 4 through 31 contain the structured total and sector-level statistics
                for (i in 4 until rows.size) {
                    val row = rows[i]
                    val codeCol = row.getOrNull(0)?.trim()
                    val sectorCol = row.getOrNull(1)?.trim() ?: ""

                    if (sectorCol.isBlank() && (codeCol == null || codeCol.isBlank() || codeCol.lowercase() == "nan")) {
                        continue
                    }

                    val record = Nis61t8Record(
                        itemCode = if (codeCol?.lowercase() == "nan") null else codeCol,
                        sector = if (sectorCol.isBlank() || sectorCol.lowercase() == "nan") "សរុប (Total)" else sectorCol,
                        enterprise2025 = cleanValue(row.getOrNull(2)),
                        totalEmployee2025 = cleanValue(row.getOrNull(3)),
                        femaleEmployee2025 = cleanValue(row.getOrNull(4)),
                        enterprise2026 = cleanValue(row.getOrNull(5)),
                        totalEmployee2026 = cleanValue(row.getOrNull(6)),
                        femaleEmployee2026 = cleanValue(row.getOrNull(7))
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

    private fun insertBatch(connection: Connection, records: List<Nis61t8Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_61t8_enterprise_employee_stats
            (item_code, sector, enterprise_2025, total_employee_2025, female_employee_2025, 
             enterprise_2026, total_employee_2026, female_employee_2026, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.itemCode)
                stmt.setString(2, r.sector)
                stmt.setString(3, r.enterprise2025)
                stmt.setString(4, r.totalEmployee2025)
                stmt.setString(5, r.femaleEmployee2025)
                stmt.setString(6, r.enterprise2026)
                stmt.setString(7, r.totalEmployee2026)
                stmt.setString(8, r.femaleEmployee2026)
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