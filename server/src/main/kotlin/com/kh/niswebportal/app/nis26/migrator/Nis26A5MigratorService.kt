package com.kh.niswebportal.app.nis26.migrator

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

data class Nis26A5Record(
    val sectionCode: String,
    val sectionName: String,
    val generation: String?,
    val description: String?,
    val numStudentsTotal: Int?,
    val numStudentsFemale: Int?,
    val numPresidingJudgeTotal: Int?,
    val numPresidingJudgeFemale: Int?,
    val numProsecutorTotal: Int?,
    val numProsecutorFemale: Int?,
    val remarks: String?
)

@Service
class Nis26A5MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_26a5"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 26A5 import." }

        val records = file.inputStream.use { parseCsv26A5(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv26A5(inputStream: InputStream): List<Nis26A5Record> {
        val records = mutableListOf<Nis26A5Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 10) return emptyList()

            for (rowIndex in 10 until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 2) continue

                val sectionCode = row.getOrNull(0)?.trim() ?: ""
                val sectionName = row.getOrNull(1)?.trim() ?: ""

                if (sectionCode.isBlank() && sectionName.isBlank()) continue

                records.add(
                    Nis26A5Record(
                        sectionCode = sectionCode,
                        sectionName = sectionName,
                        generation = row.getOrNull(2)?.trim()?.ifBlank { null },
                        description = row.getOrNull(3)?.trim()?.ifBlank { null },
                        numStudentsTotal = parseKhmerInt(row.getOrNull(4)),
                        numStudentsFemale = parseKhmerInt(row.getOrNull(5)),
                        numPresidingJudgeTotal = parseKhmerInt(row.getOrNull(6)),
                        numPresidingJudgeFemale = parseKhmerInt(row.getOrNull(7)),
                        numProsecutorTotal = parseKhmerInt(row.getOrNull(8)),
                        numProsecutorFemale = parseKhmerInt(row.getOrNull(9)),
                        remarks = row.getOrNull(10)?.trim()?.ifBlank { null }
                    )
                )
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis26A5Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_26a5_training_statistics (
                section_code, section_name_khmer, generation, description,
                num_students_total, num_students_female,
                num_presiding_judge_total, num_presiding_judge_female,
                num_prosecutor_total, num_prosecutor_female,
                remarks, upload_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (section_code, upload_id) DO UPDATE SET
                section_name_khmer = EXCLUDED.section_name_khmer,
                generation = EXCLUDED.generation,
                description = EXCLUDED.description,
                num_students_total = EXCLUDED.num_students_total,
                num_students_female = EXCLUDED.num_students_female,
                num_presiding_judge_total = EXCLUDED.num_presiding_judge_total,
                num_presiding_judge_female = EXCLUDED.num_presiding_judge_female,
                num_prosecutor_total = EXCLUDED.num_prosecutor_total,
                num_prosecutor_female = EXCLUDED.num_prosecutor_female,
                remarks = EXCLUDED.remarks
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.sectionCode)
                stmt.setString(2, r.sectionName)
                stmt.setString(3, r.generation)
                stmt.setString(4, r.description)
                setNullableInt(stmt, 5, r.numStudentsTotal)
                setNullableInt(stmt, 6, r.numStudentsFemale)
                setNullableInt(stmt, 7, r.numPresidingJudgeTotal)
                setNullableInt(stmt, 8, r.numPresidingJudgeFemale)
                setNullableInt(stmt, 9, r.numProsecutorTotal)
                setNullableInt(stmt, 10, r.numProsecutorFemale)
                stmt.setString(11, r.remarks)
                stmt.setString(12, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
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

    

    private fun setNullableInt(stmt: PreparedStatement, index: Int, value: Int?) {
        if (value != null) stmt.setInt(index, value) else stmt.setNull(index, Types.INTEGER)
    }
}