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

data class Nis61t14Record(
    val category: String,
    val itemCode: String?,
    val description: String,
    val unit: String,
    val value2025: String?,
    val value2026: String?
)

@Service
class Nis61t14MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_61t14"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 61 T14 import." }

        val records = mutableListOf<Nis61t14Record>()

        file.inputStream.use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val parser = CSVParser(reader, CSVFormat.DEFAULT)
                val rows = parser.records

                var currentCategory = "ការផ្តល់តាវកាលិកថែទាំសុខភាព (Healthcare Benefits Provision)"

                // Rows 2 through 8 contain the structured benefit statistics
                for (i in 2 until rows.size) {
                    val row = rows[i]
                    val col0 = row.getOrNull(0)?.trim()
                    val col1 = row.getOrNull(1)?.trim()
                    val col2 = row.getOrNull(2)?.trim()

                    if (col0 != null && col0.contains("I") && !col0.contains("II")) {
                        currentCategory = "ការផ្តល់តាវកាលិកថែទាំសុខភាព (Healthcare Benefits Provision)"
                        continue
                    }
                    if (col0 != null && col0.contains("II")) {
                        currentCategory = "សមាជិកទទួលតាវកាលិកថែទាំសុខភាព (Healthcare Benefits Beneficiaries)"
                        // Row 7 is 'II' header, Row 8 is 'ស្រី' sub-row
                        continue
                    }

                    val code: String?
                    val desc: String
                    val unit: String

                    if (i == 8) {
                        // Special handling for Row 8: 'ស្រី' under Category II
                        code = null
                        desc = "ស្រី (Female)"
                        unit = col2 ?: "ស្រី"
                    } else {
                        if (col0.isNullOrBlank() || col0.lowercase() == "nan") continue
                        if (col1.isNullOrBlank() || col1.lowercase() == "nan") continue

                        code = col0
                        desc = col1
                        unit = col2 ?: "ឯកសារ"
                    }

                    val record = Nis61t14Record(
                        category = currentCategory,
                        itemCode = code,
                        description = desc,
                        unit = unit,
                        value2025 = cleanValue(row.getOrNull(3)),
                        value2026 = cleanValue(row.getOrNull(4))
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

    private fun insertBatch(connection: Connection, records: List<Nis61t14Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_61t14_nssf_benefits
            (category, item_code, description, unit, value_2025, value_2026, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.category)
                stmt.setString(2, r.itemCode)
                stmt.setString(3, r.description)
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