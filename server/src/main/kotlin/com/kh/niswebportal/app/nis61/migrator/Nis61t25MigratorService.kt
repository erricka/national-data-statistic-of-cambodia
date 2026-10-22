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

data class Nis61t25Record(
    val categoryGroup: String,
    val employmentSource: String,
    val employersRecruitingCount: String?,
    val jobVacanciesCount: String?,
    val jobSeekersRegisteredCount: String?,
    val jobPlacementCount: String?
)

@Service
class Nis61t25MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_61t25"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 61 T25 import." }

        val records = mutableListOf<Nis61t25Record>()

        file.inputStream.use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val parser = CSVParser(reader, CSVFormat.DEFAULT)
                val rows = parser.records

                var currentGroup = "សរុបប្រចាំឆមាស (Bi-annual Summary)"

                // Rows 2 through 11 contain structured job opportunities breakdown rows
                for (i in 2 until rows.size) {
                    val row = rows[i]
                    val sourceCol = row.getOrNull(0)?.trim() ?: ""

                    if (sourceCol.isBlank() || sourceCol.lowercase() == "nan") {
                        continue
                    }

                    // Check if this row acts as a section header / group title
                    if (sourceCol == "សរុបប្រចាំឆមាស" || sourceCol == "សរុបទូទាំងប្រទេស") {
                        currentGroup = sourceCol
                        continue
                    }

                    val record = Nis61t25Record(
                        categoryGroup = currentGroup,
                        employmentSource = sourceCol,
                        employersRecruitingCount = cleanValue(row.getOrNull(1)),
                        jobVacanciesCount = cleanValue(row.getOrNull(2)),
                        jobSeekersRegisteredCount = cleanValue(row.getOrNull(3)),
                        jobPlacementCount = cleanValue(row.getOrNull(4))
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

    private fun insertBatch(connection: Connection, records: List<Nis61t25Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_61t25_job_opportunities_stats
            (category_group, employment_source, employers_recruiting_count, job_vacancies_count, 
             job_seekers_registered_count, job_placement_count, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.categoryGroup)
                stmt.setString(2, r.employmentSource)
                stmt.setString(3, r.employersRecruitingCount)
                stmt.setString(4, r.jobVacanciesCount)
                stmt.setString(5, r.jobSeekersRegisteredCount)
                stmt.setString(6, r.jobPlacementCount)
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