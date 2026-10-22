package com.kh.niswebportal.app.nis20.migrator

import com.kh.niswebportal.core.util.KhmerStringUtils
import com.kh.niswebportal.core.util.getOrNull
import com.kh.niswebportal.core.util.setNullableDouble
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

data class Nis20A3SanitationRecord(
    val provinceName: String,
    val year: Int,
    val basicSanitationPct: Double?,
    val odfStatus: Double?,
    val odfPlusStatus: Double?,
    val familiesUsingLatrines: Int?,
    val latrinesBuilt: Int?,
    val publicLatrinesBuilt: Int?,
    val allTypeLatrines: Int?,
    val ecoLatrines: Int?
)

@Service
class Nis20A3MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_20a3"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 20a3 import." }

        val records = file.inputStream.use { parseCsv20A3(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv20A3(inputStream: InputStream): List<Nis20A3SanitationRecord> {
        val records = mutableListOf<Nis20A3SanitationRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 6) return emptyList()

            // 1. Scan Row 2 to find which column indexes correspond to which Years
            val yearMappings = mutableListOf<Pair<Int, Int>>() // Pair<Year, ColumnIndex>
            val yearRow = rows[2]
            val yearRegex = Regex("ឆ្នាំ\\s*([០-៩0-9]{4})")

            // In 20A3, the yearly data starts at column index 8 and steps by 2
            for (c in 8 until yearRow.size() step 2) {
                val yearText = yearRow.getOrNull(c)?.trim() ?: ""
                val match = yearRegex.find(yearText)
                if (match != null) {
                    val year = parseKhmerInt(match.groupValues[1])
                    if (year != null) yearMappings.add(Pair(year, c))
                }
            }

            // 2. Extract Data (Provinces start at Row 5 in CSV/Excel)
            for (i in 5 until rows.size) {
                val row = rows[i]
                val province = row.getOrNull(1)?.trim() ?: ""

                if (province.isBlank() || province == "Pro_name") continue

                // A. Extract Overall Totals (Stored as year = 0)
                records.add(
                    Nis20A3SanitationRecord(
                        provinceName = province,
                        year = 0,
                        basicSanitationPct = KhmerStringUtils.parseKhmerDouble(row.getOrNull(2)),
                        odfStatus = KhmerStringUtils.parseKhmerDouble(row.getOrNull(3)),
                        odfPlusStatus = KhmerStringUtils.parseKhmerDouble(row.getOrNull(4)),
                        familiesUsingLatrines = parseKhmerInt(row.getOrNull(5)),
                        latrinesBuilt = parseKhmerInt(row.getOrNull(6)),
                        publicLatrinesBuilt = parseKhmerInt(row.getOrNull(7)),
                        allTypeLatrines = null,
                        ecoLatrines = null
                    )
                )

                // B. Extract Chronological Year Breakdowns
                for ((year, colIndex) in yearMappings) {
                    records.add(
                        Nis20A3SanitationRecord(
                            provinceName = province,
                            year = year,
                            basicSanitationPct = null,
                            odfStatus = null,
                            odfPlusStatus = null,
                            familiesUsingLatrines = null,
                            latrinesBuilt = null,
                            publicLatrinesBuilt = null,
                            allTypeLatrines = parseKhmerInt(row.getOrNull(colIndex)),
                            ecoLatrines = parseKhmerInt(row.getOrNull(colIndex + 1))
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis20A3SanitationRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_20a3_sanitation 
            (province_name, year, basic_sanitation_pct, odf_status, odf_plus_status, 
             families_using_latrines, latrines_built, public_latrines_built, 
             all_type_latrines, eco_latrines, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (province_name, year, upload_id) DO UPDATE SET
                basic_sanitation_pct = EXCLUDED.basic_sanitation_pct,
                odf_status = EXCLUDED.odf_status,
                odf_plus_status = EXCLUDED.odf_plus_status,
                families_using_latrines = EXCLUDED.families_using_latrines,
                latrines_built = EXCLUDED.latrines_built,
                public_latrines_built = EXCLUDED.public_latrines_built,
                all_type_latrines = EXCLUDED.all_type_latrines,
                eco_latrines = EXCLUDED.eco_latrines
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.provinceName)
                stmt.setInt(2, r.year)
                stmt.setNullableDouble( 3, r.basicSanitationPct)
                stmt.setNullableDouble( 4, r.odfStatus)
                stmt.setNullableDouble( 5, r.odfPlusStatus)
                setNullableInt(stmt, 6, r.familiesUsingLatrines)
                setNullableInt(stmt, 7, r.latrinesBuilt)
                setNullableInt(stmt, 8, r.publicLatrinesBuilt)
                setNullableInt(stmt, 9, r.allTypeLatrines)
                setNullableInt(stmt, 10, r.ecoLatrines)
                stmt.setString(11, uploadId)
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
        val cleanString = normalized.replace(Regex("[^0-9]"), "")
        return cleanString.toIntOrNull()
    }

    

    
}