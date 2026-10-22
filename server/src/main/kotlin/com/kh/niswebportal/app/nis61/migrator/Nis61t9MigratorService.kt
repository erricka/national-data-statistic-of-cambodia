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

data class Nis61t9Record(
    val itemCode: String?,
    val description: String,
    val subDescription: String?,
    val unit: String,
    val value2025: String?,
    val value2026: String?
)

@Service
class Nis61t9MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_61t9"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 61 T9 import." }

        val records = mutableListOf<Nis61t9Record>()

        file.inputStream.use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val parser = CSVParser(reader, CSVFormat.DEFAULT)
                val rows = parser.records

                var currentItemCode: String? = null
                var currentDesc: String = ""

                // Rows 2 through 16 contain the professional relations and labor dispute statistics
                for (i in 2 until rows.size) {
                    val row = rows[i]
                    val codeCol = row.getOrNull(0)?.trim()
                    val descCol1 = row.getOrNull(1)?.trim()
                    val descCol2 = row.getOrNull(2)?.trim()
                    val unitCol = row.getOrNull(3)?.trim() ?: ""

                    if (codeCol != null && codeCol.isNotBlank() && codeCol.lowercase() != "nan") {
                        currentItemCode = codeCol
                    }

                    // Determine main description and sub-description layout based on row structure
                    if (descCol1 != null && descCol1.isNotBlank() && descCol1.lowercase() != "nan") {
                        currentDesc = descCol1
                        if (descCol2 != null && descCol2.isNotBlank() && descCol2.lowercase() != "nan") {
                            // Sub-description exists in col 2, unit is in col 3
                            val record = Nis61t9Record(
                                itemCode = currentItemCode,
                                description = currentDesc,
                                subDescription = descCol2,
                                unit = unitCol,
                                value2025 = cleanValue(row.getOrNull(4)),
                                value2026 = cleanValue(row.getOrNull(5))
                            )
                            records.add(record)
                            continue
                        }
                    } else if (descCol2 != null && descCol2.isNotBlank() && descCol2.lowercase() != "nan") {
                        // Multi-line sub-item under the same main item
                        val record = Nis61t9Record(
                            itemCode = currentItemCode,
                            description = currentDesc,
                            subDescription = descCol2,
                            unit = unitCol,
                            value2025 = cleanValue(row.getOrNull(4)),
                            value2026 = cleanValue(row.getOrNull(5))
                        )
                        records.add(record)
                        continue
                    }

                    // Single-level entries (Items 3, 4, 5, 6, 7) where col 1 is description and col 2 is unit
                    val singleUnitCol = descCol2 ?: unitCol
                    val record = Nis61t9Record(
                        itemCode = currentItemCode,
                        description = currentDesc.ifBlank { descCol1 ?: "" },
                        subDescription = null,
                        unit = singleUnitCol,
                        value2025 = cleanValue(row.getOrNull(4)),
                        value2026 = cleanValue(row.getOrNull(5))
                    )

                    if (record.description.isNotBlank() && record.description.lowercase() != "nan") {
                        records.add(record)
                    }
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

    private fun insertBatch(connection: Connection, records: List<Nis61t9Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_61t9_professional_relations
            (item_code, description, sub_description, unit, value_2025, value_2026, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.itemCode)
                stmt.setString(2, r.description)
                stmt.setString(3, r.subDescription)
                stmt.setString(4, r.unit)
                stmt.setString(5, r.value2025)
                stmt.setString(6, r.value2026)
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