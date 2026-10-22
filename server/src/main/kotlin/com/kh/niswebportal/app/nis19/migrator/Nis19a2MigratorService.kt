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

data class Nis19a2Record(
    val provinceCode: String,
    val provinceName: String,
    val landForest2016: String?,
    val landForest2018: String?,
    val landForest2020: String?,
    val landForest2022: String?,
    val landForest2024: String?,
    val landForest2026: String?,
    val forestCover2016: String?,
    val forestCover2018: String?,
    val forestCover2020: String?,
    val forestCover2022: String?,
    val forestCover2024: String?,
    val forestCover2026: String?
)

@Service
class Nis19a2MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_19a2"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 19 A2 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv19a2(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv19a2(inputStream: InputStream): List<Nis19a2Record> {
        val records = mutableListOf<Nis19a2Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 16) return emptyList()

            for (i in 16 until rows.size) {
                val row = rows[i]

                val codeRaw = row.getOrNull(0)?.trim() ?: ""
                val nameRaw = row.getOrNull(1)?.trim() ?: ""

                if (codeRaw.equals("Pro_Code", ignoreCase = true) || nameRaw.equals("Pro_name", ignoreCase = true)) {
                    continue
                }

                val effectiveCode = when {
                    codeRaw.isNotBlank() -> codeRaw.toLatinDigits()
                    nameRaw.contains("សរុប") || codeRaw.contains("សរុប") -> "TOTAL"
                    else -> continue
                }

                val effectiveName = if (nameRaw.isNotBlank()) nameRaw else if (effectiveCode == "TOTAL") "សរុប" else continue

                records.add(
                    Nis19a2Record(
                        provinceCode = effectiveCode,
                        provinceName = effectiveName,
                        landForest2016 = cleanValue(row.getOrNull(2)),
                        landForest2018 = cleanValue(row.getOrNull(3)),
                        landForest2020 = cleanValue(row.getOrNull(4)),
                        landForest2022 = cleanValue(row.getOrNull(5)),
                        landForest2024 = cleanValue(row.getOrNull(6)),
                        landForest2026 = cleanValue(row.getOrNull(7)),
                        forestCover2016 = cleanValue(row.getOrNull(8)),
                        forestCover2018 = cleanValue(row.getOrNull(9)),
                        forestCover2020 = cleanValue(row.getOrNull(10)),
                        forestCover2022 = cleanValue(row.getOrNull(11)),
                        forestCover2024 = cleanValue(row.getOrNull(12)),
                        forestCover2026 = cleanValue(row.getOrNull(13))
                    )
                )
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis19a2Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_19a2_province_forest_stats
            (province_code, province_name, land_forest_2016, land_forest_2018, land_forest_2020, land_forest_2022, land_forest_2024, land_forest_2026, forest_cover_2016, forest_cover_2018, forest_cover_2020, forest_cover_2022, forest_cover_2024, forest_cover_2026, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (province_code, upload_id) DO UPDATE SET
                province_name = EXCLUDED.province_name,
                land_forest_2016 = EXCLUDED.land_forest_2016,
                land_forest_2018 = EXCLUDED.land_forest_2018,
                land_forest_2020 = EXCLUDED.land_forest_2020,
                land_forest_2022 = EXCLUDED.land_forest_2022,
                land_forest_2024 = EXCLUDED.land_forest_2024,
                land_forest_2026 = EXCLUDED.land_forest_2026,
                forest_cover_2016 = EXCLUDED.forest_cover_2016,
                forest_cover_2018 = EXCLUDED.forest_cover_2018,
                forest_cover_2020 = EXCLUDED.forest_cover_2020,
                forest_cover_2022 = EXCLUDED.forest_cover_2022,
                forest_cover_2024 = EXCLUDED.forest_cover_2024,
                forest_cover_2026 = EXCLUDED.forest_cover_2026
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.provinceCode)
                stmt.setString(2, r.provinceName)
                stmt.setString(3, r.landForest2016)
                stmt.setString(4, r.landForest2018)
                stmt.setString(5, r.landForest2020)
                stmt.setString(6, r.landForest2022)
                stmt.setString(7, r.landForest2024)
                stmt.setString(8, r.landForest2026)
                stmt.setString(9, r.forestCover2016)
                stmt.setString(10, r.forestCover2018)
                stmt.setString(11, r.forestCover2020)
                stmt.setString(12, r.forestCover2022)
                stmt.setString(13, r.forestCover2024)
                stmt.setString(14, r.forestCover2026)
                stmt.setString(15, uploadId)
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