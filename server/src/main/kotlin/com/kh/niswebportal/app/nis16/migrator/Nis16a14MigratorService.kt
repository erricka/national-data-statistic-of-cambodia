package com.kh.niswebportal.app.nis16.migrator

import com.kh.niswebportal.core.strategy.NisMigratorStrategy
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream
import java.io.InputStreamReader
import java.sql.Connection
import javax.sql.DataSource

data class Nis16a14Record(
    val year: String,
    val gender: String,
    val prischProm: String?,
    val prischRep: String?,
    val prischDrop: String?,
    val lsProm: String?,
    val lsRep: String?,
    val lsDrop: String?,
    val usProm: String?,
    val usRep: String?,
    val usDrop: String?,
    val canpsLv6: String?,
    val canpsLv9: String?,
    val canpsLv12: String?
)

@Service
class Nis16a14MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_16a14"

    private companion object {
        private const val BATCH_SIZE = 1000
        private val KHMER_TO_LATIN_MAP = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for nis_16a14 import." }

        val records = file.inputStream.use { parseCsv(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
//                connection.prepareStatement("DELETE FROM nis_16a14").use { it.executeUpdate() }
//
//                connection.prepareStatement("DELETE FROM datasource_history WHERE source_id = ?").use { stmt ->
//                    stmt.setString(1, sourceId)
//                    stmt.executeUpdate()
//                }

                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv(inputStream: InputStream): List<Nis16a14Record> {
        val records = mutableListOf<Nis16a14Record>()

        InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 10) return emptyList()

            var currentGender = "BOTH"

            for (i in 10 until rows.size) {
                val row = rows[i]
                val col0 = row.getOrNull(0)?.trim() ?: ""

                if (col0.isBlank() || col0.lowercase() == "nan") continue

                if (col0.contains("ប្រុស")) {
                    currentGender = "MALE"
                    continue
                } else if (col0.contains("ស្រី")) {
                    currentGender = "FEMALE"
                    continue
                } else if (col0.contains("ប្រភព") || col0.contains("Source")) {
                    break
                }

                val year = cleanValue(col0)?.substringBefore(".") ?: continue

                records.add(
                    Nis16a14Record(
                        year = year,
                        gender = currentGender,
                        prischProm = cleanValue(row.getOrNull(1)),
                        prischRep = cleanValue(row.getOrNull(2)),
                        prischDrop = cleanValue(row.getOrNull(3)),
                        lsProm = cleanValue(row.getOrNull(4)),
                        lsRep = cleanValue(row.getOrNull(5)),
                        lsDrop = cleanValue(row.getOrNull(6)),
                        usProm = cleanValue(row.getOrNull(7)),
                        usRep = cleanValue(row.getOrNull(8)),
                        usDrop = cleanValue(row.getOrNull(9)),
                        canpsLv6 = cleanValue(row.getOrNull(10)),
                        canpsLv9 = cleanValue(row.getOrNull(11)),
                        canpsLv12 = cleanValue(row.getOrNull(12))
                    )
                )
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis16a14Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_16a14
            (year, gender, prisch_prom, prisch_rep, prisch_drop, ls_prom, ls_rep, ls_drop, us_prom, us_rep, us_drop, canps_lv6, canps_lv9, canps_lv12, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, gender, upload_id) DO UPDATE SET
                prisch_prom = EXCLUDED.prisch_prom,
                prisch_rep = EXCLUDED.prisch_rep,
                prisch_drop = EXCLUDED.prisch_drop,
                ls_prom = EXCLUDED.ls_prom,
                ls_rep = EXCLUDED.ls_rep,
                ls_drop = EXCLUDED.ls_drop,
                us_prom = EXCLUDED.us_prom,
                us_rep = EXCLUDED.us_rep,
                us_drop = EXCLUDED.us_drop,
                canps_lv6 = EXCLUDED.canps_lv6,
                canps_lv9 = EXCLUDED.canps_lv9,
                canps_lv12 = EXCLUDED.canps_lv12
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.year)
                stmt.setString(2, r.gender)
                stmt.setString(3, r.prischProm)
                stmt.setString(4, r.prischRep)
                stmt.setString(5, r.prischDrop)
                stmt.setString(6, r.lsProm)
                stmt.setString(7, r.lsRep)
                stmt.setString(8, r.lsDrop)
                stmt.setString(9, r.usProm)
                stmt.setString(10, r.usRep)
                stmt.setString(11, r.usDrop)
                stmt.setString(12, r.canpsLv6)
                stmt.setString(13, r.canpsLv9)
                stmt.setString(14, r.canpsLv12)
                stmt.setString(15, uploadId)
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

    private fun cleanValue(value: String?): String? {
        val cleaned = value?.trim()?.replace(" ", "")?.replace(",", "") ?: return null
        if (cleaned.isBlank() || cleaned == "-" || cleaned.lowercase() == "n.a" || cleaned.lowercase() == "nan") return null
        return cleaned.toLatinDigits()
    }

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}