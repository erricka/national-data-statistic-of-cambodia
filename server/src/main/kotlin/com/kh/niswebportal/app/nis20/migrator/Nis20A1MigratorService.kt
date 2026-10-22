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

data class Nis20A1RoadRecord(
    val provinceName: String,
    val year: Int,
    val totalLength: Double?,
    val totalLines: Int?,
    val dbstLength: Double?,
    val concreteLength: Double?,
    val gravelLength: Double?,
    val bridges: Int?,
    val boxCulverts: Int?,
    val pipeCulverts: Int?
)

@Service
class Nis20A1MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_20a1"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 20a1 import." }

        val records = file.inputStream.use { parseCsv20A1(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv20A1(inputStream: InputStream): List<Nis20A1RoadRecord> {
        val records = mutableListOf<Nis20A1RoadRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 6) return emptyList()

            // 1. Scan Row 2 to find which column indexes correspond to which Years
            val yearMappings = mutableListOf<Pair<Int, Int>>() // Pair<Year, ColumnIndex>
            val yearRow = rows[2]
            val yearRegex = Regex("ឆ្នាំ\\s*([០-៩0-9]{4})")

            for (c in 13 until yearRow.size() step 3) {
                val yearText = yearRow.getOrNull(c)?.trim() ?: ""
                val match = yearRegex.find(yearText)
                if (match != null) {
                    val year = parseKhmerInt(match.groupValues[1])
                    if (year != null) yearMappings.add(Pair(year, c))
                }
            }

            // 2. Extract Data (Provinces start at Row 5 in CSV, index 5)
            for (i in 5 until rows.size) {
                val row = rows[i]
                val province = row.getOrNull(1)?.trim() ?: ""

                if (province.isBlank() || province == "Pro_name") continue

                // A. Extract Overall Totals (Stored as year = 0)
                records.add(
                    Nis20A1RoadRecord(
                        provinceName = province,
                        year = 0,
                        totalLength = KhmerStringUtils.parseKhmerDouble(row.getOrNull(2)),
                        totalLines = parseKhmerInt(row.getOrNull(3)),
                        dbstLength = KhmerStringUtils.parseKhmerDouble(row.getOrNull(4)),
                        concreteLength = KhmerStringUtils.parseKhmerDouble(row.getOrNull(5)),
                        gravelLength = KhmerStringUtils.parseKhmerDouble(row.getOrNull(6)),
                        bridges = parseKhmerInt(row.getOrNull(7)),
                        boxCulverts = parseKhmerInt(row.getOrNull(8)),
                        pipeCulverts = parseKhmerInt(row.getOrNull(9))
                    )
                )

                // B. Extract Chronological Year Breakdowns
                for ((year, colIndex) in yearMappings) {
                    records.add(
                        Nis20A1RoadRecord(
                            provinceName = province,
                            year = year,
                            totalLength = null,
                            totalLines = null,
                            dbstLength = KhmerStringUtils.parseKhmerDouble(row.getOrNull(colIndex)),
                            concreteLength = KhmerStringUtils.parseKhmerDouble(row.getOrNull(colIndex + 1)),
                            gravelLength = KhmerStringUtils.parseKhmerDouble(row.getOrNull(colIndex + 2)),
                            bridges = null,
                            boxCulverts = null,
                            pipeCulverts = null
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis20A1RoadRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_20a1_rural_roads 
            (province_name, year, total_length, total_lines, dbst_length, concrete_length, 
             gravel_length, bridges, box_culverts, pipe_culverts, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (province_name, year, upload_id) DO UPDATE SET
                total_length = EXCLUDED.total_length,
                total_lines = EXCLUDED.total_lines,
                dbst_length = EXCLUDED.dbst_length,
                concrete_length = EXCLUDED.concrete_length,
                gravel_length = EXCLUDED.gravel_length,
                bridges = EXCLUDED.bridges,
                box_culverts = EXCLUDED.box_culverts,
                pipe_culverts = EXCLUDED.pipe_culverts
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.provinceName)
                stmt.setInt(2, r.year)
                stmt.setNullableDouble( 3, r.totalLength)
                setNullableInt(stmt, 4, r.totalLines)
                stmt.setNullableDouble( 5, r.dbstLength)
                stmt.setNullableDouble( 6, r.concreteLength)
                stmt.setNullableDouble( 7, r.gravelLength)
                setNullableInt(stmt, 8, r.bridges)
                setNullableInt(stmt, 9, r.boxCulverts)
                setNullableInt(stmt, 10, r.pipeCulverts)
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