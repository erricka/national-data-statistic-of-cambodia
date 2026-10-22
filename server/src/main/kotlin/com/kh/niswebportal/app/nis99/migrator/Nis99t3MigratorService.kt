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

data class SectorCapitalRecord(
    val yearPeriod: String,
    val agriProjectsCount: String?,
    val agriTotalCost: String?,
    val agriWorkingCapital: String?,
    val agriFixedAsset: String?,
    val indProjectsCount: String?,
    val indTotalCost: String?,
    val indWorkingCapital: String?,
    val indFixedAsset: String?,
    val infraProjectsCount: String?,
    val infraTotalCost: String?,
    val infraWorkingCapital: String?,
    val infraFixedAsset: String?,
    val tourProjectsCount: String?,
    val tourTotalCost: String?,
    val tourWorkingCapital: String?,
    val tourFixedAsset: String?
)

@Service
class Nis99t3MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_99t3"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 99 T3 import." }

        val records = mutableListOf<SectorCapitalRecord>()

        file.inputStream.use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val parser = CSVParser(reader, CSVFormat.DEFAULT)
                val rows = parser.records

                for (i in 13 until rows.size) {
                    val row = rows[i]
                    val yearCol = row.getOrNull(1)?.trim() ?: ""
                    val lowerYearCol = yearCol.lowercase()

                    if (lowerYearCol.startsWith("source") || lowerYearCol.startsWith("note") ||
                        lowerYearCol.startsWith("1/") || lowerYearCol.startsWith("2/")) {
                        break
                    }

                    if (yearCol.isBlank() || lowerYearCol == "nan") {
                        continue
                    }

                    val record = SectorCapitalRecord(
                        yearPeriod = yearCol,
                        agriProjectsCount = cleanValue(row.getOrNull(3)),
                        agriTotalCost = cleanValue(row.getOrNull(4)),
                        agriWorkingCapital = cleanValue(row.getOrNull(5)),
                        agriFixedAsset = cleanValue(row.getOrNull(6)),

                        indProjectsCount = cleanValue(row.getOrNull(7)),
                        indTotalCost = cleanValue(row.getOrNull(8)),
                        indWorkingCapital = cleanValue(row.getOrNull(9)),
                        indFixedAsset = cleanValue(row.getOrNull(10)),

                        infraProjectsCount = cleanValue(row.getOrNull(11)),
                        infraTotalCost = cleanValue(row.getOrNull(12)),
                        infraWorkingCapital = cleanValue(row.getOrNull(13)),
                        infraFixedAsset = cleanValue(row.getOrNull(14)),

                        tourProjectsCount = cleanValue(row.getOrNull(15)),
                        tourTotalCost = cleanValue(row.getOrNull(16)),
                        tourWorkingCapital = cleanValue(row.getOrNull(17)),
                        tourFixedAsset = cleanValue(row.getOrNull(18))
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

    private fun insertBatch(connection: Connection, records: List<SectorCapitalRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_99t3_sector_capital
            (year_period, agri_projects_count, agri_total_cost, agri_working_capital, agri_fixed_asset,
             ind_projects_count, ind_total_cost, ind_working_capital, ind_fixed_asset,
             infra_projects_count, infra_total_cost, infra_working_capital, infra_fixed_asset,
             tour_projects_count, tour_total_cost, tour_working_capital, tour_fixed_asset, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.yearPeriod)
                stmt.setString(2, r.agriProjectsCount)
                stmt.setString(3, r.agriTotalCost)
                stmt.setString(4, r.agriWorkingCapital)
                stmt.setString(5, r.agriFixedAsset)
                stmt.setString(6, r.indProjectsCount)
                stmt.setString(7, r.indTotalCost)
                stmt.setString(8, r.indWorkingCapital)
                stmt.setString(9, r.indFixedAsset)
                stmt.setString(10, r.infraProjectsCount)
                stmt.setString(11, r.infraTotalCost)
                stmt.setString(12, r.infraWorkingCapital)
                stmt.setString(13, r.infraFixedAsset)
                stmt.setString(14, r.tourProjectsCount)
                stmt.setString(15, r.tourTotalCost)
                stmt.setString(16, r.tourWorkingCapital)
                stmt.setString(17, r.tourFixedAsset)
                stmt.setString(18, uploadId)
                stmt.addBatch()

                count++
                if (count % BATCH_SIZE == 0) stmt.executeBatch()
            }
            if (count % BATCH_SIZE != 0) stmt.executeBatch()
        }
    }

    private fun cleanValue(value: String?): String? {
        val cleaned = value?.trim()?.replace(",", "") ?: return null
        if (cleaned.isBlank() || cleaned == "-" || cleaned == "nan" || cleaned.contains("%")) return null
        return cleaned
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}