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

data class Nis61t20Record(
    val categoryGroup: String,
    val indicatorName: String,
    val employedFormal: String?,
    val employedInformal: String?,
    val employedTotal: String?,
    val unemployed: String?,
    val outsideLabourForce: String?,
    val labourForceParticipationRate: String?
)

@Service
class Nis61t20MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_61t20"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 61 T20 import." }

        val records = mutableListOf<Nis61t20Record>()

        file.inputStream.use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val parser = CSVParser(reader, CSVFormat.DEFAULT)
                val rows = parser.records

                var currentGroup = "សរុប (General)"

                // Rows 3 through 51 contain structured LFS breakdown rows
                for (i in 3 until rows.size) {
                    val row = rows[i]
                    val indicator = row.getOrNull(0)?.trim() ?: ""

                    if (indicator.isBlank() || indicator.lowercase() == "nan") {
                        continue
                    }

                    // Check if this row acts as a section header / group title
                    if (indicator == "តាមភេទ" || indicator == "តាមក្រុមអាយុ" ||
                        indicator == "កម្រិតសិក្សា" || indicator == "តាមវិស័យ" ||
                        indicator == "តាមកម្រិតជំនាញ") {
                        currentGroup = indicator
                        continue
                    }

                    val record = Nis61t20Record(
                        categoryGroup = currentGroup,
                        indicatorName = indicator,
                        employedFormal = cleanValue(row.getOrNull(1)),
                        employedInformal = cleanValue(row.getOrNull(2)),
                        employedTotal = cleanValue(row.getOrNull(3)),
                        unemployed = cleanValue(row.getOrNull(4)),
                        outsideLabourForce = cleanValue(row.getOrNull(5)),
                        labourForceParticipationRate = cleanValue(row.getOrNull(6))
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

    private fun insertBatch(connection: Connection, records: List<Nis61t20Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_61t20_labour_force_stats
            (category_group, indicator_name, employed_formal, employed_informal, 
             employed_total, unemployed, outside_labour_force, labour_force_participation_rate, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.categoryGroup)
                stmt.setString(2, r.indicatorName)
                stmt.setString(3, r.employedFormal)
                stmt.setString(4, r.employedInformal)
                stmt.setString(5, r.employedTotal)
                stmt.setString(6, r.unemployed)
                stmt.setString(7, r.outsideLabourForce)
                stmt.setString(8, r.labourForceParticipationRate)
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