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
import java.sql.PreparedStatement
import java.sql.Types
import javax.sql.DataSource

data class Nis09A2ForeignMissionRecord(
    val itemCode: String,
    val countryName: String,
    val year: Int,
    val numMissions: Int?
)

@Service
class Nis09A2MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_09a2"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 09a2 import." }

        val records = file.inputStream.use { parseCsv09A2(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv09A2(inputStream: InputStream): List<Nis09A2ForeignMissionRecord> {
        val records = mutableListOf<Nis09A2ForeignMissionRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 4) return emptyList()

            // Row 2 contains the Years (e.g., 2016, 2017)
            val yearRow = rows[2]

            for (i in 4 until rows.size) {
                val row = rows[i]

                // Fallback auto-generation in case the user forgets to type the ID
                val itemCode = row.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() } ?: "09A2_R$i"

                // The data steps by 2 columns for each year (Country | Count)
                for (c in 1 until yearRow.size() step 2) {
                    val yearText = yearRow.getOrNull(c)?.trim()
                    val year = parseKhmerInt(yearText) ?: continue

                    val country = row.getOrNull(c)?.trim() ?: ""
                    val countText = row.getOrNull(c + 1)?.trim() ?: ""
                    val count = parseKhmerInt(countText)

                    if (country.isNotBlank()) {
                        records.add(
                            Nis09A2ForeignMissionRecord(
                                itemCode = itemCode,
                                countryName = country,
                                year = year,
                                numMissions = count
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis09A2ForeignMissionRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_09a2_foreign_missions 
            (item_code, country_name, year, num_missions, upload_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (item_code, year, upload_id) DO UPDATE SET
                country_name = EXCLUDED.country_name,
                num_missions = EXCLUDED.num_missions
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.itemCode)
                stmt.setString(2, r.countryName)
                stmt.setInt(3, r.year)
                setNullableInt(stmt, 4, r.numMissions)
                stmt.setString(5, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun setNullableInt(stmt: PreparedStatement, index: Int, value: Int?) {
        if (value != null) stmt.setInt(index, value) else stmt.setNull(index, Types.INTEGER)
    }

    private fun parseKhmerInt(text: String?): Int? {
        if (text.isNullOrBlank() || text.trim() == "-") return null
        val khmerToLatinDigits = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
        val normalized = text.map { khmerToLatinDigits[it] ?: it }.joinToString("")
        val cleanString = normalized.replace(Regex("[^0-9.]"), "")
        return cleanString.toDoubleOrNull()?.toInt()
    }

    
}