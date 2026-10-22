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

data class Nis61t21Record(
    val categoryGroup: String,
    val transitionStatus: String,
    val sampleSize: String?,
    val transited: String?,
    val inTransition: String?,
    val notYetStarted: String?
)

@Service
class Nis61t21MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_61t21"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 61 T21 import." }

        val records = mutableListOf<Nis61t21Record>()

        file.inputStream.use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val parser = CSVParser(reader, CSVFormat.DEFAULT)
                val rows = parser.records

                var currentGroup = "សរុប (General)"

                // Rows 2 through 17 contain structured SWTS breakdown rows
                for (i in 2 until rows.size) {
                    val row = rows[i]
                    val statusCol = row.getOrNull(0)?.trim() ?: ""

                    if (statusCol.isBlank() || statusCol.lowercase() == "nan") {
                        continue
                    }

                    // Check if this row acts as a section header / group title
                    if (statusCol == "តាមកម្រិតអប់រំ" || statusCol == "តាមភេទ" || statusCol == "តាមក្រុមអាយុ") {
                        currentGroup = statusCol
                        continue
                    }

                    val record = Nis61t21Record(
                        categoryGroup = currentGroup,
                        transitionStatus = statusCol,
                        sampleSize = cleanValue(row.getOrNull(1)),
                        transited = cleanValue(row.getOrNull(2)),
                        inTransition = cleanValue(row.getOrNull(3)),
                        notYetStarted = cleanValue(row.getOrNull(4))
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

    private fun insertBatch(connection: Connection, records: List<Nis61t21Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_61t21_swts_stats
            (category_group, transition_status, sample_size, transited, in_transition, not_yet_started, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.categoryGroup)
                stmt.setString(2, r.transitionStatus)
                stmt.setString(3, r.sampleSize)
                stmt.setString(4, r.transited)
                stmt.setString(5, r.inTransition)
                stmt.setString(6, r.notYetStarted)
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