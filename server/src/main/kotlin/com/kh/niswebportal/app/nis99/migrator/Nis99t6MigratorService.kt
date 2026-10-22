package com.kh.niswebportal.app.nis99.migrator

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

data class SectorCapitalRecordT6(
    val yearPeriod: String,
    val agriCapital: String?,
    val industryCapital: String?,
    val infraCapital: String?,
    val tourismCapital: String?,
    val totalCapital: String?,
    val agriShare: String?,
    val industryShare: String?,
    val infraShare: String?,
    val tourismShare: String?,
    val totalShare: String?,
    val agriGrowth: String?,
    val industryGrowth: String?,
    val infraGrowth: String?,
    val tourismGrowth: String?,
    val totalGrowth: String?
)

@Service
class Nis99t6MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_99t6"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 99 T6 import." }

        val records = mutableListOf<SectorCapitalRecordT6>()

        file.inputStream.use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val parser = CSVParser(reader, CSVFormat.DEFAULT)
                val rows = parser.records

                for (i in 12 until rows.size) {
                    val row = rows[i]
                    val yearCol = row.getOrNull(1)?.trim() ?: ""
                    val lowerYearCol = yearCol.lowercase()

                    if (lowerYearCol.startsWith("source") || lowerYearCol.startsWith("notes") ||
                        lowerYearCol.startsWith("*") || lowerYearCol.startsWith("1/")) {
                        break
                    }

                    if (yearCol.isBlank() || lowerYearCol == "nan") {
                        continue
                    }

                    val record = SectorCapitalRecordT6(
                        yearPeriod = yearCol,
                        agriCapital = cleanValue(row.getOrNull(3)),
                        industryCapital = cleanValue(row.getOrNull(4)),
                        infraCapital = cleanValue(row.getOrNull(5)),
                        tourismCapital = cleanValue(row.getOrNull(6)),
                        totalCapital = cleanValue(row.getOrNull(7)),
                        agriShare = cleanValue(row.getOrNull(8)),
                        industryShare = cleanValue(row.getOrNull(9)),
                        infraShare = cleanValue(row.getOrNull(10)),
                        tourismShare = cleanValue(row.getOrNull(11)),
                        totalShare = cleanValue(row.getOrNull(12)),
                        agriGrowth = cleanValue(row.getOrNull(13)),
                        industryGrowth = cleanValue(row.getOrNull(14)),
                        infraGrowth = cleanValue(row.getOrNull(15)),
                        tourismGrowth = cleanValue(row.getOrNull(16)),
                        totalGrowth = cleanValue(row.getOrNull(17))
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

    private fun insertBatch(connection: Connection, records: List<SectorCapitalRecordT6>, uploadId: String) {
        val sql = """
            INSERT INTO nis_99t6_sector_capital
            (year_period, agri_capital, industry_capital, infra_capital, tourism_capital, total_capital, 
             agri_share, industry_share, infra_share, tourism_share, total_share, 
             agri_growth, industry_growth, infra_growth, tourism_growth, total_growth, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.yearPeriod)
                stmt.setString(2, r.agriCapital)
                stmt.setString(3, r.industryCapital)
                stmt.setString(4, r.infraCapital)
                stmt.setString(5, r.tourismCapital)
                stmt.setString(6, r.totalCapital)
                stmt.setString(7, r.agriShare)
                stmt.setString(8, r.industryShare)
                stmt.setString(9, r.infraShare)
                stmt.setString(10, r.tourismShare)
                stmt.setString(11, r.totalShare)
                stmt.setString(12, r.agriGrowth)
                stmt.setString(13, r.industryGrowth)
                stmt.setString(14, r.infraGrowth)
                stmt.setString(15, r.tourismGrowth)
                stmt.setString(16, r.totalGrowth)
                stmt.setString(17, uploadId)
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