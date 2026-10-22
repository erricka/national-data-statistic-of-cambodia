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

data class Nis61t27Record(
    val categoryGroup: String,
    val breakdownItem: String,
    val surveyedCount: String?,
    val averageMonthlyBaseWage: String?,
    val allowancesOrOvertime: String?,
    val estimatedLaborProductivityIndex: String?
)

@Service
class Nis61t27MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_61t27"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 61 T27 import." }

        val records = mutableListOf<Nis61t27Record>()

        file.inputStream.use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val parser = CSVParser(reader, CSVFormat.DEFAULT)
                val rows = parser.records

                var currentGroup = "សរុប (General Summary)"

                // Rows 2 through 50 contain structured breakdown rows
                for (i in 2 until rows.size) {
                    val row = rows[i]
                    val itemCol = row.getOrNull(0)?.trim() ?: ""

                    if (itemCol.isBlank() || itemCol.lowercase() == "nan") {
                        continue
                    }

                    // Check if this row acts as a section header / group title
                    if (itemCol == "តាមភេទ" || itemCol == "តាមក្រុមអាយុ" || itemCol == "កម្រិតសិក្សា" ||
                        itemCol == "តាមវិស័យ" || itemCol == "តាមកម្រិតជំនាញ") {
                        currentGroup = itemCol
                        continue
                    }

                    val record = Nis61t27Record(
                        categoryGroup = currentGroup,
                        breakdownItem = itemCol,
                        surveyedCount = cleanValue(row.getOrNull(1)),
                        averageMonthlyBaseWage = cleanValue(row.getOrNull(2)),
                        allowancesOrOvertime = cleanValue(row.getOrNull(3)),
                        estimatedLaborProductivityIndex = cleanValue(row.getOrNull(4))
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

    private fun insertBatch(connection: Connection, records: List<Nis61t27Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_61t27_wages_productivity_stats
            (category_group, breakdown_item, surveyed_count, average_monthly_base_wage, 
             allowances_or_overtime, estimated_labor_productivity_index, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.categoryGroup)
                stmt.setString(2, r.breakdownItem)
                stmt.setString(3, r.surveyedCount)
                stmt.setString(4, r.averageMonthlyBaseWage)
                stmt.setString(5, r.allowancesOrOvertime)
                stmt.setString(6, r.estimatedLaborProductivityIndex)
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