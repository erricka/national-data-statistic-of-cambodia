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

data class Nis20A2WellRecord(
    val provinceName: String,
    val year: Int,
    val basicWaterPct: Double?,
    val cleanWaterFamilies: Int?,
    val climateWells: Int?,
    val communityPonds: Int?,
    val rainTank4m3: Int?,
    val rainTank35m3: Int?,
    val pipeSystems: Int?,
    val drinkingStations: Int?,
    val filterTanks: Int?,
    val deepWells: Int?,
    val mixedWells: Int?,
    val ponds: Int?
)

@Service
class Nis20A2MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_20a2"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 20a2 import." }

        val records = file.inputStream.use { parseCsv20A2(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv20A2(inputStream: InputStream): List<Nis20A2WellRecord> {
        val records = mutableListOf<Nis20A2WellRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 6) return emptyList()

            // 1. Scan Row 2 to find which column indexes correspond to which Years
            val yearMappings = mutableListOf<Pair<Int, Int>>() // Pair<Year, ColumnIndex>
            val yearRow = rows[2]
            val yearRegex = Regex("ឆ្នាំ\\s*([០-៩0-9]{4})")

            for (c in 11 until yearRow.size() step 3) {
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
                    Nis20A2WellRecord(
                        provinceName = province,
                        year = 0,
                        basicWaterPct = KhmerStringUtils.parseKhmerDouble(row.getOrNull(2)),
                        cleanWaterFamilies = parseKhmerInt(row.getOrNull(3)),
                        climateWells = parseKhmerInt(row.getOrNull(4)),
                        communityPonds = parseKhmerInt(row.getOrNull(5)),
                        rainTank4m3 = parseKhmerInt(row.getOrNull(6)),
                        rainTank35m3 = parseKhmerInt(row.getOrNull(7)),
                        pipeSystems = parseKhmerInt(row.getOrNull(8)),
                        drinkingStations = parseKhmerInt(row.getOrNull(9)),
                        filterTanks = parseKhmerInt(row.getOrNull(10)),
                        deepWells = null,
                        mixedWells = null,
                        ponds = null
                    )
                )

                // B. Extract Chronological Year Breakdowns
                for ((year, colIndex) in yearMappings) {
                    records.add(
                        Nis20A2WellRecord(
                            provinceName = province,
                            year = year,
                            basicWaterPct = null,
                            cleanWaterFamilies = null,
                            climateWells = null,
                            communityPonds = null,
                            rainTank4m3 = null,
                            rainTank35m3 = null,
                            pipeSystems = null,
                            drinkingStations = null,
                            filterTanks = null,
                            deepWells = parseKhmerInt(row.getOrNull(colIndex)),
                            mixedWells = parseKhmerInt(row.getOrNull(colIndex + 1)),
                            ponds = parseKhmerInt(row.getOrNull(colIndex + 2))
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis20A2WellRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_20a2_wells_ponds 
            (province_name, year, basic_water_pct, clean_water_families, climate_wells, community_ponds, 
             rain_tank_4m3, rain_tank_35m3, pipe_systems, drinking_stations, filter_tanks, 
             deep_wells, mixed_wells, ponds, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (province_name, year, upload_id) DO UPDATE SET
                basic_water_pct = EXCLUDED.basic_water_pct,
                clean_water_families = EXCLUDED.clean_water_families,
                climate_wells = EXCLUDED.climate_wells,
                community_ponds = EXCLUDED.community_ponds,
                rain_tank_4m3 = EXCLUDED.rain_tank_4m3,
                rain_tank_35m3 = EXCLUDED.rain_tank_35m3,
                pipe_systems = EXCLUDED.pipe_systems,
                drinking_stations = EXCLUDED.drinking_stations,
                filter_tanks = EXCLUDED.filter_tanks,
                deep_wells = EXCLUDED.deep_wells,
                mixed_wells = EXCLUDED.mixed_wells,
                ponds = EXCLUDED.ponds
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.provinceName)
                stmt.setInt(2, r.year)
                stmt.setNullableDouble( 3, r.basicWaterPct)
                setNullableInt(stmt, 4, r.cleanWaterFamilies)
                setNullableInt(stmt, 5, r.climateWells)
                setNullableInt(stmt, 6, r.communityPonds)
                setNullableInt(stmt, 7, r.rainTank4m3)
                setNullableInt(stmt, 8, r.rainTank35m3)
                setNullableInt(stmt, 9, r.pipeSystems)
                setNullableInt(stmt, 10, r.drinkingStations)
                setNullableInt(stmt, 11, r.filterTanks)
                setNullableInt(stmt, 12, r.deepWells)
                setNullableInt(stmt, 13, r.mixedWells)
                setNullableInt(stmt, 14, r.ponds)
                stmt.setString(15, uploadId)
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