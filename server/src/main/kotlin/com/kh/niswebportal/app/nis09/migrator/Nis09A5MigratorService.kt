package com.kh.niswebportal.app.nis09.migrator

import com.kh.niswebportal.core.util.getOrNull
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

data class Nis09A5VisaRecord(
    val itemCode: String,
    val countryKh: String,
    val countryEn: String,
    val cityKh: String,
    val cityEn: String,
    val diplomatic: String,
    val official: String,
    val special: String,
    val ordinary: String,
    val effectiveDate: String
)

@Service
class Nis09A5MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_09a5"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 09a5 import." }

        val records = file.inputStream.use { parseCsv09A5(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv09A5(inputStream: InputStream): List<Nis09A5VisaRecord> {
        val records = mutableListOf<Nis09A5VisaRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 5) return emptyList()

            // Data starts at row index 2 in our CSV (index 4 or 5 in Excel depending on hidden rows).
            // We just detect the item code.
            for (i in 2 until rows.size) {
                val row = rows[i]
                val itemCode = row.getOrNull(0)?.trim() ?: ""

                // Safety fix: the blank excel had a typo (09A2_1). Ensure it's treated as data if it has an underscore.
                if (itemCode.contains("_") && itemCode.isNotBlank()) {
                    val countryKh = row.getOrNull(1)?.trim() ?: ""

                    if (countryKh.isNotBlank()) {
                        records.add(
                            Nis09A5VisaRecord(
                                itemCode = itemCode.replace("09A2", "09A5"), // Fixes Excel typo on the fly
                                countryKh = countryKh,
                                countryEn = row.getOrNull(2)?.trim() ?: "",
                                cityKh = row.getOrNull(3)?.trim() ?: "",
                                cityEn = row.getOrNull(4)?.trim() ?: "",
                                diplomatic = row.getOrNull(5)?.trim() ?: "",
                                official = row.getOrNull(6)?.trim() ?: "",
                                special = row.getOrNull(7)?.trim() ?: "",
                                ordinary = row.getOrNull(8)?.trim() ?: "",
                                effectiveDate = row.getOrNull(9)?.trim() ?: ""
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis09A5VisaRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_09a5_visa_exemptions 
            (item_code, country_kh, country_en, city_kh, city_en, 
             diplomatic_days, official_days, special_days, ordinary_days, effective_date, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (item_code, upload_id) DO UPDATE SET
                country_kh = EXCLUDED.country_kh,
                country_en = EXCLUDED.country_en,
                city_kh = EXCLUDED.city_kh,
                city_en = EXCLUDED.city_en,
                diplomatic_days = EXCLUDED.diplomatic_days,
                official_days = EXCLUDED.official_days,
                special_days = EXCLUDED.special_days,
                ordinary_days = EXCLUDED.ordinary_days,
                effective_date = EXCLUDED.effective_date
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.itemCode)
                stmt.setString(2, r.countryKh)
                stmt.setString(3, r.countryEn)
                stmt.setString(4, r.cityKh)
                stmt.setString(5, r.cityEn)
                stmt.setString(6, r.diplomatic)
                stmt.setString(7, r.official)
                stmt.setString(8, r.special)
                stmt.setString(9, r.ordinary)
                stmt.setString(10, r.effectiveDate)
                stmt.setString(11, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    
}