package com.kh.niswebportal.app.nis07.migrator

import com.kh.niswebportal.core.strategy.NisMigratorStrategy
import com.kh.niswebportal.core.util.KhmerStringUtils
import com.kh.niswebportal.core.util.getOrNull
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

data class Nis07A4CriminalOffenseRecord(
    val offenseCode: String,
    val offenseName: String,
    val yearPrev: Int,
    val yearCurr: Int,
    val changeCount: Int,
    val changePercent: Double,
    val totalDamage: String
)

@Service
class Nis07A4MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_07a4"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val records = file.inputStream.use { inputStream ->
            parseCsv07A4(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv07A4(inputStream: InputStream): List<Nis07A4CriminalOffenseRecord> {
        val records = mutableListOf<Nis07A4CriminalOffenseRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 10) return emptyList()

            // Header span rows 0..9; Data rows start at index 10
            for (rowIndex in 10 until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 2) continue

                val code = row.getOrNull(0)?.trim() ?: ""
                val name = row.getOrNull(1)?.trim() ?: ""

                if (code.isBlank() && name.isBlank()) continue

                records.add(
                    Nis07A4CriminalOffenseRecord(
                        offenseCode = code,
                        offenseName = name,
                        yearPrev = KhmerStringUtils.parseKhmerInt(row.getOrNull(2)) ?: 0,
                        yearCurr = KhmerStringUtils.parseKhmerInt(row.getOrNull(3)) ?: 0,
                        changeCount = KhmerStringUtils.parseKhmerInt(row.getOrNull(4)) ?: 0,
                        changePercent = KhmerStringUtils.parseKhmerDouble(row.getOrNull(5)) ?: 0.0,
                        totalDamage = row.getOrNull(6)?.trim() ?: ""
                    )
                )
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis07A4CriminalOffenseRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_07a4_criminal_offenses
            (offense_code, offense_name, year_prev, year_curr, change_count, change_percent, total_damage, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (offense_code, upload_id) DO UPDATE SET
                offense_name = EXCLUDED.offense_name,
                year_prev = EXCLUDED.year_prev,
                year_curr = EXCLUDED.year_curr,
                change_count = EXCLUDED.change_count,
                change_percent = EXCLUDED.change_percent,
                total_damage = EXCLUDED.total_damage
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.offenseCode)
                stmt.setString(2, r.offenseName)
                stmt.setInt(3, r.yearPrev)
                stmt.setInt(4, r.yearCurr)
                stmt.setInt(5, r.changeCount)
                stmt.setDouble(6, r.changePercent)
                stmt.setString(7, r.totalDamage)
                stmt.setString(8, uploadId)
                stmt.addBatch()

                count++
                if (count % BATCH_SIZE == 0) {
                    stmt.executeBatch()
                }
            }
            if (count % BATCH_SIZE != 0) {
                stmt.executeBatch()
            }
        }
    }
}
