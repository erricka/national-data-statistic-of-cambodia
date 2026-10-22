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

data class Nis61t26Record(
    val categoryGroup: String,
    val expenseType: String,
    val sampleSize: String?,
    val numericValue: String?,
    val unit: String?,
    val percentageOfMonthlyWage: String?,
    val familyMembersChange: String?,
    val changeVsPreviousYearPct: String?
)

@Service
class Nis61t26MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_61t26"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 61 T26 import." }

        val records = mutableListOf<Nis61t26Record>()

        file.inputStream.use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val parser = CSVParser(reader, CSVFormat.DEFAULT)
                val rows = parser.records

                var currentGroup = "ថ្លៃចំណាយរស់នៅ (Living Expenses)"

                // Rows 2 through 9 contain structured living conditions breakdown rows
                for (i in 2 until rows.size) {
                    val row = rows[i]
                    val typeCol = row.getOrNull(0)?.trim() ?: ""

                    if (typeCol.isBlank() || typeCol.lowercase() == "nan") {
                        continue
                    }

                    // Check if this row acts as a section header / group title
                    if (typeCol == "បន្ទុកគ្រួសារ") {
                        currentGroup = typeCol
                        continue
                    }

                    val record = if (currentGroup == "ថ្លៃចំណាយរស់នៅ (Living Expenses)") {
                        Nis61t26Record(
                            categoryGroup = currentGroup,
                            expenseType = typeCol,
                            sampleSize = cleanValue(row.getOrNull(1)),
                            numericValue = cleanValue(row.getOrNull(2)),
                            unit = cleanValue(row.getOrNull(3)),
                            percentageOfMonthlyWage = cleanValue(row.getOrNull(4)),
                            familyMembersChange = null,
                            changeVsPreviousYearPct = cleanValue(row.getOrNull(5))
                        )
                    } else {
                        Nis61t26Record(
                            categoryGroup = currentGroup,
                            expenseType = typeCol,
                            sampleSize = cleanValue(row.getOrNull(1)),
                            numericValue = cleanValue(row.getOrNull(2)),
                            unit = cleanValue(row.getOrNull(3)),
                            percentageOfMonthlyWage = null,
                            familyMembersChange = cleanValue(row.getOrNull(4)),
                            changeVsPreviousYearPct = cleanValue(row.getOrNull(5))
                        )
                    }

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

    private fun insertBatch(connection: Connection, records: List<Nis61t26Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_61t26_workers_living_conditions
            (category_group, expense_type, sample_size, numeric_value, unit, 
             percentage_of_monthly_wage, family_members_change, change_vs_previous_year_pct, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.categoryGroup)
                stmt.setString(2, r.expenseType)
                stmt.setString(3, r.sampleSize)
                stmt.setString(4, r.numericValue)
                stmt.setString(5, r.unit)
                stmt.setString(6, r.percentageOfMonthlyWage)
                stmt.setString(7, r.familyMembersChange)
                stmt.setString(8, r.changeVsPreviousYearPct)
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