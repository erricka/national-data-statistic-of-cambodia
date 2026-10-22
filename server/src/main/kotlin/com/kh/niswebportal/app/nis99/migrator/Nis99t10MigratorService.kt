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

data class CdcSezRecord(
    val yearPeriod: String,
    val sector: String,
    val china: String?,
    val singapore: String?,
    val cambodia: String?,
    val usa: String?,
    val vietnam: String?,
    val caymanIslands: String?,
    val british: String?,
    val japan: String?,
    val marshallIslands: String?,
    val canada: String?,
    val saintLucia: String?,
    val australia: String?,
    val totalCapital: String?
)

@Service
class Nis99t10MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_99t10"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 99 T10 import." }

        val records = mutableListOf<CdcSezRecord>()

        file.inputStream.use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val parser = CSVParser(reader, CSVFormat.DEFAULT)
                val rows = parser.records

                var currentYear = "2025" // Default active block based on layout structure

                for (i in 80 until rows.size) {
                    val row = rows[i]
                    val yearCol = row.getOrNull(1)?.trim() ?: ""
                    val sectorCol = row.getOrNull(2)?.trim() ?: ""

                    if (yearCol.isNotBlank() && yearCol.lowercase() != "nan" && !yearCol.startsWith("Year")) {
                        currentYear = yearCol.replace(" ", "")
                    }

                    if (sectorCol.isBlank() || sectorCol.lowercase() == "nan" || sectorCol.equals("Total", ignoreCase = true)) {
                        continue
                    }

                    val record = CdcSezRecord(
                        yearPeriod = currentYear,
                        sector = sectorCol,
                        china = cleanValue(row.getOrNull(3)),
                        singapore = cleanValue(row.getOrNull(4)),
                        cambodia = cleanValue(row.getOrNull(5)),
                        usa = cleanValue(row.getOrNull(6)),
                        vietnam = cleanValue(row.getOrNull(7)),
                        caymanIslands = cleanValue(row.getOrNull(8)),
                        british = cleanValue(row.getOrNull(9)),
                        japan = cleanValue(row.getOrNull(10)),
                        marshallIslands = cleanValue(row.getOrNull(11)),
                        canada = cleanValue(row.getOrNull(12)),
                        saintLucia = cleanValue(row.getOrNull(13)),
                        australia = cleanValue(row.getOrNull(14)),
                        totalCapital = cleanValue(row.getOrNull(15))
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

    private fun insertBatch(connection: Connection, records: List<CdcSezRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_99t10_cdc_sez
            (year_period, sector, country_china, country_singapore, country_cambodia, country_usa, country_vietnam, 
             country_cayman_islands, country_british, country_japan, country_marshall_islands, country_canada, 
             country_saint_lucia, country_australia, total_capital, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.yearPeriod)
                stmt.setString(2, r.sector)
                stmt.setString(3, r.china)
                stmt.setString(4, r.singapore)
                stmt.setString(5, r.cambodia)
                stmt.setString(6, r.usa)
                stmt.setString(7, r.vietnam)
                stmt.setString(8, r.caymanIslands)
                stmt.setString(9, r.british)
                stmt.setString(10, r.japan)
                stmt.setString(11, r.marshallIslands)
                stmt.setString(12, r.canada)
                stmt.setString(13, r.saintLucia)
                stmt.setString(14, r.australia)
                stmt.setString(15, r.totalCapital)
                stmt.setString(16, uploadId)
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