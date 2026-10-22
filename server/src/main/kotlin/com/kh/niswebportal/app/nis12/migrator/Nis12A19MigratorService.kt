package com.kh.niswebportal.app.nis12.migrator

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
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Types
import javax.sql.DataSource

data class Nis12A19Record(
    val levelCode: String,
    val levelNo: String?,
    val levelNameKh: String,
    val year: Int,
    val physiciansCount: BigDecimal?,
    val docsNursesCount: BigDecimal?,
    val otherStaffCount: BigDecimal?,
    val patientsCount: BigDecimal?,
    val bedsCount: BigDecimal?,
    val othersCount: BigDecimal?
)

@Service
class Nis12A19MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_12a19"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 12a19 import." }

        val records = file.inputStream.use { parseCsv12A19(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv12A19(inputStream: InputStream): List<Nis12A19Record> {
        val records = mutableListOf<Nis12A19Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            for (row in rows) {
                val levelCode = row.getOrNull(0)?.trim() ?: ""

                // Match valid data rows (levelCode starts with 12a19_ and is not metadata)
                if (levelCode.startsWith("12a19_") && levelCode != "12a19") {
                    val levelNo = row.getOrNull(1)?.trim()
                    val levelNameKhRaw = row.getOrNull(2)?.trim()
                    val levelNameKh = if (!levelNameKhRaw.isNullOrBlank()) levelNameKhRaw else (levelNo ?: "")

                    // Parse Year 2023 values (cols 3..8)
                    val rec2023 = Nis12A19Record(
                        levelCode = levelCode,
                        levelNo = levelNo,
                        levelNameKh = levelNameKh,
                        year = 2023,
                        physiciansCount = parseDecimalValue(row.getOrNull(3)),
                        docsNursesCount = parseDecimalValue(row.getOrNull(4)),
                        otherStaffCount = parseDecimalValue(row.getOrNull(5)),
                        patientsCount = parseDecimalValue(row.getOrNull(6)),
                        bedsCount = parseDecimalValue(row.getOrNull(7)),
                        othersCount = parseDecimalValue(row.getOrNull(8))
                    )

                    // Parse Year 2024 values (cols 9..14)
                    val rec2024 = Nis12A19Record(
                        levelCode = levelCode,
                        levelNo = levelNo,
                        levelNameKh = levelNameKh,
                        year = 2024,
                        physiciansCount = parseDecimalValue(row.getOrNull(9)),
                        docsNursesCount = parseDecimalValue(row.getOrNull(10)),
                        otherStaffCount = parseDecimalValue(row.getOrNull(11)),
                        patientsCount = parseDecimalValue(row.getOrNull(12)),
                        bedsCount = parseDecimalValue(row.getOrNull(13)),
                        othersCount = parseDecimalValue(row.getOrNull(14))
                    )

                    records.add(rec2023)
                    records.add(rec2024)
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis12A19Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_12a19_health_staff_by_facility 
            (level_code, level_no, level_name_kh, year, physicians_count, docs_nurses_count, other_staff_count, patients_count, beds_count, others_count, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (level_code, year, upload_id) DO UPDATE SET
                level_no = EXCLUDED.level_no,
                level_name_kh = EXCLUDED.level_name_kh,
                physicians_count = EXCLUDED.physicians_count,
                docs_nurses_count = EXCLUDED.docs_nurses_count,
                other_staff_count = EXCLUDED.other_staff_count,
                patients_count = EXCLUDED.patients_count,
                beds_count = EXCLUDED.beds_count,
                others_count = EXCLUDED.others_count
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.levelCode)
                stmt.setString(2, r.levelNo)
                stmt.setString(3, r.levelNameKh)
                stmt.setInt(4, r.year)
                setNullableBigDecimal(stmt, 5, r.physiciansCount)
                setNullableBigDecimal(stmt, 6, r.docsNursesCount)
                setNullableBigDecimal(stmt, 7, r.otherStaffCount)
                setNullableBigDecimal(stmt, 8, r.patientsCount)
                setNullableBigDecimal(stmt, 9, r.bedsCount)
                setNullableBigDecimal(stmt, 10, r.othersCount)
                stmt.setString(11, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun setNullableBigDecimal(stmt: PreparedStatement, index: Int, value: BigDecimal?) {
        if (value != null) stmt.setBigDecimal(index, value) else stmt.setNull(index, Types.NUMERIC)
    }

    private fun parseDecimalValue(text: String?): BigDecimal? {
        if (text.isNullOrBlank() || text.trim() == "-") return null
        val khmerToLatinDigits = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
        val normalized = text.map { khmerToLatinDigits[it] ?: it }.joinToString("").replace(",", "").trim()
        val digitsOnly = normalized.replace(Regex("[^0-9.]"), "")
        return digitsOnly.toBigDecimalOrNull()
    }

    
}