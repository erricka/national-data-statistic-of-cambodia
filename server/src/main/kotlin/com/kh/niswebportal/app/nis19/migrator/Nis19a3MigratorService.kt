package com.kh.niswebportal.app.nis19.migrator

import com.kh.niswebportal.core.strategy.NisMigratorStrategy
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.sql.Connection
import javax.sql.DataSource

data class Nis19a3Record(
    val categoryCode: String,
    val managementCategory: String,
    val numberOfAreas: String?,
    val ecosystemCode: String?,
    val mainEcosystemCoverage: String?,
    val areaInHectares: String?
)

@Service
class Nis19a3MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_19a3"

    private companion object {
        private const val BATCH_SIZE = 1000
        private val KHMER_TO_LATIN_MAP = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 19 A3 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv19a3(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv19a3(inputStream: InputStream): List<Nis19a3Record> {
        val records = mutableListOf<Nis19a3Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 11) return emptyList()

            for (i in 12 until rows.size) {
                val row = rows[i]

                val codeRaw = row.getOrNull(0)?.trim() ?: ""
                val catCol1 = row.getOrNull(1)?.trim() ?: ""
                val catCol2 = row.getOrNull(2)?.trim() ?: ""

                val managementCat = when {
                    catCol1.isNotBlank() -> catCol1
                    catCol2.isNotBlank() -> catCol2
                    else -> continue
                }

                if (managementCat.startsWith("Source", ignoreCase = true)) {
                    continue
                }

                val effectiveCode = if (codeRaw.isBlank()) {
                    "19A3_CAT_${i - 11}"
                } else {
                    codeRaw
                }

                val numAreas = cleanValue(row.getOrNull(4))
                val ecoCode = row.getOrNull(5)?.trim()?.ifBlank { null }
                val ecoCoverage = row.getOrNull(6)?.trim()?.ifBlank { null }
                val areaHa = cleanValue(row.getOrNull(7))

                records.add(
                    Nis19a3Record(
                        categoryCode = effectiveCode,
                        managementCategory = managementCat,
                        numberOfAreas = numAreas,
                        ecosystemCode = ecoCode,
                        mainEcosystemCoverage = ecoCoverage,
                        areaInHectares = areaHa
                    )
                )
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis19a3Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_19a3_ecosystems_protected_areas
            (category_code, management_category, number_of_areas, ecosystem_code, main_ecosystem_coverage, area_in_hectares, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (category_code, upload_id) DO UPDATE SET
                management_category = EXCLUDED.management_category,
                number_of_areas = EXCLUDED.number_of_areas,
                ecosystem_code = EXCLUDED.ecosystem_code,
                main_ecosystem_coverage = EXCLUDED.main_ecosystem_coverage,
                area_in_hectares = EXCLUDED.area_in_hectares
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.categoryCode)
                stmt.setString(2, r.managementCategory)
                stmt.setString(3, r.numberOfAreas)
                stmt.setString(4, r.ecosystemCode)
                stmt.setString(5, r.mainEcosystemCoverage)
                stmt.setString(6, r.areaInHectares)
                stmt.setString(7, uploadId)
                stmt.addBatch()

                count++
                if (count % BATCH_SIZE == 0) {
                    stmt.executeBatch()
                }
            }
            if (count % BATCH_SIZE != 0) {
                stmt.executeBatch()
            }
        }
    }

    private fun cleanValue(value: String?): String? {
        val cleaned = value?.trim()?.replace(" ", "")?.replace(",", "") ?: return null
        if (cleaned.isBlank() || cleaned == "-" || cleaned == "nan") return null
        return cleaned.toLatinDigits()
    }

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}