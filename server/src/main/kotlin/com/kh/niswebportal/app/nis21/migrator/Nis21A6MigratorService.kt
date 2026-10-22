package com.kh.niswebportal.app.nis21.migrator

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

data class Nis21A6Record(
    val provinceCode: String?,
    val provinceNameKhmer: String,
    val year: Int,
    val numRetirees: Int?,
    val numLossCapacity: Int?,
    val numDisabled: Int?,
    val numMartyrs: Int?,
    val numDeceased: Int?,
    val numDependents: Int?
)

private data class Nis21A6YearColumnMapping(
    val year: Int,
    val startColIndex: Int
)

@Service
class Nis21A6MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_21a6"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 21A6 import." }

        val records = file.inputStream.use { parseCsv21A6(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv21A6(inputStream: InputStream): List<Nis21A6Record> {
        val records = mutableListOf<Nis21A6Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.isEmpty()) return emptyList()

            // Locate header row containing 'លទ្ធផល'
            var headerRowIndex = -1
            for (i in rows.indices) {
                val cellText = rows[i].getOrNull(1)?.trim()
                if (cellText == "លទ្ធផល") {
                    headerRowIndex = i
                    break
                }
            }

            if (headerRowIndex == -1) return emptyList()

            val yearHeaderRow = rows[headerRowIndex]
            val yearMappings = detectYearMappings(yearHeaderRow)

            if (yearMappings.isEmpty()) return emptyList()

            // Data rows start after header rows (Total row, followed by province rows)
            for (rowIndex in (headerRowIndex + 2) until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 2) continue

                val provinceCode = row.getOrNull(0)?.trim()
                val provinceNameKhmer = row.getOrNull(1)?.trim() ?: ""

                if (provinceNameKhmer.isBlank()) continue

                for (mapping in yearMappings) {
                    val idx = mapping.startColIndex
                    val numRetirees = parseKhmerInt(row.getOrNull(idx))
                    val numLossCapacity = parseKhmerInt(row.getOrNull(idx + 1))
                    val numDisabled = parseKhmerInt(row.getOrNull(idx + 2))
                    val numMartyrs = parseKhmerInt(row.getOrNull(idx + 3))
                    val numDeceased = parseKhmerInt(row.getOrNull(idx + 4))
                    val numDependents = parseKhmerInt(row.getOrNull(idx + 5))

                    if (listOf(numRetirees, numLossCapacity, numDisabled, numMartyrs, numDeceased, numDependents).any { it != null }) {
                        records.add(
                            Nis21A6Record(
                                provinceCode = if (provinceCode.isNullOrBlank()) null else provinceCode,
                                provinceNameKhmer = provinceNameKhmer,
                                year = mapping.year,
                                numRetirees = numRetirees,
                                numLossCapacity = numLossCapacity,
                                numDisabled = numDisabled,
                                numMartyrs = numMartyrs,
                                numDeceased = numDeceased,
                                numDependents = numDependents
                            )
                        )
                    }
                }
            }
        }
        return records
    }

    private fun detectYearMappings(yearHeaderRow: CSVRecord): List<Nis21A6YearColumnMapping> {
        val mappings = mutableListOf<Nis21A6YearColumnMapping>()

        for (colIndex in 2 until yearHeaderRow.size()) {
            val cellText = yearHeaderRow.getOrNull(colIndex)?.trim() ?: ""
            val year = parseKhmerInt(cellText)

            if (year != null && year > 2000) {
                mappings.add(Nis21A6YearColumnMapping(year = year, startColIndex = colIndex))
            }
        }
        return mappings
    }

    private fun insertBatch(connection: Connection, records: List<Nis21A6Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_21a6_veterans_dependents 
            (province_code, province_name_khmer, year, num_retirees, num_loss_capacity, num_disabled, num_martyrs, num_deceased, num_dependents, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (province_name_khmer, year, upload_id) DO UPDATE SET
                province_code = EXCLUDED.province_code,
                num_retirees = EXCLUDED.num_retirees,
                num_loss_capacity = EXCLUDED.num_loss_capacity,
                num_disabled = EXCLUDED.num_disabled,
                num_martyrs = EXCLUDED.num_martyrs,
                num_deceased = EXCLUDED.num_deceased,
                num_dependents = EXCLUDED.num_dependents
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.provinceCode)
                stmt.setString(2, r.provinceNameKhmer)
                stmt.setInt(3, r.year)
                setNullableInt(stmt, 4, r.numRetirees)
                setNullableInt(stmt, 5, r.numLossCapacity)
                setNullableInt(stmt, 6, r.numDisabled)
                setNullableInt(stmt, 7, r.numMartyrs)
                setNullableInt(stmt, 8, r.numDeceased)
                setNullableInt(stmt, 9, r.numDependents)
                stmt.setString(10, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun setNullableInt(stmt: PreparedStatement, index: Int, value: Int?) {
        if (value != null) stmt.setInt(index, value) else stmt.setNull(index, Types.INTEGER)
    }

    private fun parseKhmerInt(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val khmerToLatinDigits = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
        val normalized = text.map { khmerToLatinDigits[it] ?: it }.joinToString("")
        val digitsOnly = normalized.replace(Regex("[^0-9]"), "")
        return digitsOnly.toIntOrNull()
    }

    
}