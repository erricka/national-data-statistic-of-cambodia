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

data class Nis19a1Record(
    val categoryCode: String,
    val categoryName: String,
    val location: String?,
    val physiographicRegion: String?,
    val parkAreaThousandHa: String?,
    val forestCoverAreaThousandHa: String?,
    val salientFeatures: String?
)

@Service
class Nis19a1MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_19a1"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 19 A1 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv19a1(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv19a1(inputStream: InputStream): List<Nis19a1Record> {
        val records = mutableListOf<Nis19a1Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 11) return emptyList()

            for (i in 12 until rows.size) {
                val row = rows[i]

                val codeRaw = row.getOrNull(0)?.trim() ?: ""
                val nameRaw = row.getOrNull(1)?.trim() ?: ""

                if (nameRaw.isBlank() || nameRaw.startsWith("Total", ignoreCase = true) || nameRaw.startsWith("Source", ignoreCase = true)) {
                    continue
                }

                val effectiveCode = if (codeRaw.isBlank()) {
                    "19A1_CAT_${i - 11}"
                } else {
                    codeRaw
                }

                val locationVal = row.getOrNull(3)?.trim()?.ifBlank { null }
                val regionVal = row.getOrNull(4)?.trim()?.ifBlank { null }
                val parkAreaVal = cleanValue(row.getOrNull(5))
                val forestCoverVal = cleanValue(row.getOrNull(6))
                val featuresVal = row.getOrNull(7)?.trim()?.ifBlank { null }

                records.add(
                    Nis19a1Record(
                        categoryCode = effectiveCode,
                        categoryName = nameRaw,
                        location = locationVal,
                        physiographicRegion = regionVal,
                        parkAreaThousandHa = parkAreaVal,
                        forestCoverAreaThousandHa = forestCoverVal,
                        salientFeatures = featuresVal
                    )
                )
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis19a1Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_19a1_protected_areas
            (category_code, category_name, location, physiographic_region, park_area_thousand_ha, forest_cover_area_thousand_ha, salient_features, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (category_code, upload_id) DO UPDATE SET
                category_name = EXCLUDED.category_name,
                location = EXCLUDED.location,
                physiographic_region = EXCLUDED.physiographic_region,
                park_area_thousand_ha = EXCLUDED.park_area_thousand_ha,
                forest_cover_area_thousand_ha = EXCLUDED.forest_cover_area_thousand_ha,
                salient_features = EXCLUDED.salient_features
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.categoryCode)
                stmt.setString(2, r.categoryName)
                stmt.setString(3, r.location)
                stmt.setString(4, r.physiographicRegion)
                stmt.setString(5, r.parkAreaThousandHa)
                stmt.setString(6, r.forestCoverAreaThousandHa)
                stmt.setString(7, r.salientFeatures)
                stmt.setString(8, uploadId)
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