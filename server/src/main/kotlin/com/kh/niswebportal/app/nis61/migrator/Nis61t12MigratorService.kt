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

data class Nis61t12Record(
    val sector: String,
    val year: String,
    val totalPersons: String?,
    val totalFemale: String?,
    val workplaceInjuryTotal: String?,
    val workplaceInjuryFemale: String?,
    val commuteInjuryTotal: String?,
    val commuteInjuryFemale: String?
)

@Service
class Nis61t12MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_61t12"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 61 T12 import." }

        val records = mutableListOf<Nis61t12Record>()

        file.inputStream.use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val parser = CSVParser(reader, CSVFormat.DEFAULT)
                val rows = parser.records

                var currentSector = "សរុប (១+២) (Total)"

                // Rows 4 through 12 contain the categorized breakdown by sector and year
                for (i in 4 until rows.size) {
                    val row = rows[i]
                    val col0 = row.getOrNull(0)?.trim() ?: ""

                    if (col0.contains("សរុប") || col0.contains("១+២")) {
                        currentSector = "សរុប (១+២) (Total)"
                        continue
                    }
                    if (col0.contains("វិស័យឯកជន") || col0.contains("១- វិស័យឯកជន")) {
                        currentSector = "វិស័យឯកជន (Private Sector)"
                        continue
                    }
                    if (col0.contains("វិស័យសាធារណៈ") || col0.contains("២- វិស័យសាធារណៈ")) {
                        currentSector = "វិស័យសាធារណៈ (Public Sector)"
                        continue
                    }

                    val yearClean = col0.replace(".", "").trim()
                    if (yearClean == "2025" || yearClean == "2026") {
                        val record = Nis61t12Record(
                            sector = currentSector,
                            year = yearClean,
                            totalPersons = cleanValue(row.getOrNull(1)),
                            totalFemale = cleanValue(row.getOrNull(2)),
                            workplaceInjuryTotal = cleanValue(row.getOrNull(3)),
                            workplaceInjuryFemale = cleanValue(row.getOrNull(4)),
                            commuteInjuryTotal = cleanValue(row.getOrNull(5)),
                            commuteInjuryFemale = cleanValue(row.getOrNull(6))
                        )
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

    private fun insertBatch(connection: Connection, records: List<Nis61t12Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_61t12_occupational_injuries
            (sector, year, total_persons, total_female, workplace_injury_total, 
             workplace_injury_female, commute_injury_total, commute_injury_female, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.sector)
                stmt.setString(2, r.year)
                stmt.setString(3, r.totalPersons)
                stmt.setString(4, r.totalFemale)
                stmt.setString(5, r.workplaceInjuryTotal)
                stmt.setString(6, r.workplaceInjuryFemale)
                stmt.setString(7, r.commuteInjuryTotal)
                stmt.setString(8, r.commuteInjuryFemale)
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