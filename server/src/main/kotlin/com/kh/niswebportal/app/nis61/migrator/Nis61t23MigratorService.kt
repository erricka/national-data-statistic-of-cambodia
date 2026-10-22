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

data class Nis61t23Record(
    val categoryGroup: String,
    val indicatorName: String,
    val totalGraduates: String?,
    val employedOrSelfEmployed: String?,
    val furtherEducation: String?,
    val lookingForWork: String?,
    val employmentRate: String?,
    val overEducatedMismatch: String?,
    val underEducatedMismatch: String?,
    val overSkillingMismatch: String?,
    val underSkillingMismatch: String?,
    val fieldOfStudyMismatch: String?
)

@Service
class Nis61t23MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_61t23"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 61 T23 import." }

        val records = mutableListOf<Nis61t23Record>()

        file.inputStream.use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val parser = CSVParser(reader, CSVFormat.DEFAULT)
                val rows = parser.records

                var currentGroup = "សរុប (General)"

                // Rows 2 through 20 contain structured TVET employment breakdown rows
                for (i in 2 until rows.size) {
                    val row = rows[i]
                    val indicator = row.getOrNull(0)?.trim() ?: ""

                    if (indicator.isBlank() || indicator.lowercase() == "nan") {
                        continue
                    }

                    // Check if this row acts as a section header / group title
                    if (indicator == "តាមភេទ" || indicator == "តាមក្រុមអាយុ" || indicator == "តាមកម្រិតអប់រំ") {
                        currentGroup = indicator
                        continue
                    }

                    val record = Nis61t23Record(
                        categoryGroup = currentGroup,
                        indicatorName = indicator,
                        totalGraduates = cleanValue(row.getOrNull(1)),
                        employedOrSelfEmployed = cleanValue(row.getOrNull(2)),
                        furtherEducation = cleanValue(row.getOrNull(3)),
                        lookingForWork = cleanValue(row.getOrNull(4)),
                        employmentRate = cleanValue(row.getOrNull(5)),
                        overEducatedMismatch = cleanValue(row.getOrNull(6)),
                        underEducatedMismatch = cleanValue(row.getOrNull(7)),
                        overSkillingMismatch = cleanValue(row.getOrNull(8)),
                        underSkillingMismatch = cleanValue(row.getOrNull(9)),
                        fieldOfStudyMismatch = cleanValue(row.getOrNull(10))
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

    private fun insertBatch(connection: Connection, records: List<Nis61t23Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_61t23_tvet_employment_stats
            (category_group, indicator_name, total_graduates, employed_or_self_employed, 
             further_education, looking_for_work, employment_rate, over_educated_mismatch, 
             under_educated_mismatch, over_skilling_mismatch, under_skilling_mismatch, 
             field_of_study_mismatch, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.categoryGroup)
                stmt.setString(2, r.indicatorName)
                stmt.setString(3, r.totalGraduates)
                stmt.setString(4, r.employedOrSelfEmployed)
                stmt.setString(5, r.furtherEducation)
                stmt.setString(6, r.lookingForWork)
                stmt.setString(7, r.employmentRate)
                stmt.setString(8, r.overEducatedMismatch)
                stmt.setString(9, r.underEducatedMismatch)
                stmt.setString(10, r.overSkillingMismatch)
                stmt.setString(11, r.underSkillingMismatch)
                stmt.setString(12, r.fieldOfStudyMismatch)
                stmt.setString(13, uploadId)
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