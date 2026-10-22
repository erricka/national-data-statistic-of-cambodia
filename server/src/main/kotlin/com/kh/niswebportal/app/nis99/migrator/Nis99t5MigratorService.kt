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

data class FixedAssetsCountryRecord(
    val yearPeriod: String,
    val japan: String?,
    val usa: String?,
    val euTotal: String?,
    val eastAsiaKorea: String?,
    val eastAsiaChina: String?,
    val eastAsiaOther: String?,
    val eastAsiaTotal: String?,
    val asean: String?,
    val otherCountries: String?,
    val totalFixedAssets: String?
)

@Service
class Nis99t5MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_99t5"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 99 T5 import." }

        val records = mutableListOf<FixedAssetsCountryRecord>()

        file.inputStream.use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val parser = CSVParser(reader, CSVFormat.DEFAULT)
                val rows = parser.records

                for (i in 14 until rows.size) {
                    val row = rows[i]
                    val yearCol = row.getOrNull(1)?.trim() ?: ""
                    val lowerYearCol = yearCol.lowercase()

                    if (lowerYearCol.startsWith("source") || lowerYearCol.startsWith("notes") ||
                        lowerYearCol.startsWith("*") || lowerYearCol.startsWith("1/") || lowerYearCol.startsWith("2/") || lowerYearCol.startsWith("3/")) {
                        break
                    }

                    if (yearCol.isBlank() || lowerYearCol == "nan") {
                        continue
                    }

                    val record = FixedAssetsCountryRecord(
                        yearPeriod = yearCol,
                        japan = cleanValue(row.getOrNull(2)),
                        usa = cleanValue(row.getOrNull(4)),
                        euTotal = cleanValue(row.getOrNull(5)),
                        eastAsiaKorea = cleanValue(row.getOrNull(6)),
                        eastAsiaChina = cleanValue(row.getOrNull(7)),
                        eastAsiaOther = cleanValue(row.getOrNull(9)),
                        eastAsiaTotal = cleanValue(row.getOrNull(10)),
                        asean = cleanValue(row.getOrNull(11)),
                        otherCountries = cleanValue(row.getOrNull(12)),
                        totalFixedAssets = cleanValue(row.getOrNull(14))
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

    private fun insertBatch(connection: Connection, records: List<FixedAssetsCountryRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_99t5_fixed_assets_country
            (year_period, japan, usa, eu_total, east_asia_korea, east_asia_china, east_asia_other, east_asia_total, asean, other_countries, total_fixed_assets, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.yearPeriod)
                stmt.setString(2, r.japan)
                stmt.setString(3, r.usa)
                stmt.setString(4, r.euTotal)
                stmt.setString(5, r.eastAsiaKorea)
                stmt.setString(6, r.eastAsiaChina)
                stmt.setString(7, r.eastAsiaOther)
                stmt.setString(8, r.eastAsiaTotal)
                stmt.setString(9, r.asean)
                stmt.setString(10, r.otherCountries)
                stmt.setString(11, r.totalFixedAssets)
                stmt.setString(12, uploadId)
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