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

data class Nis16b1Record(
    val year: String,
    val gender: String,
    val pubTch: String?,
    val pubStf: String?,
    val pubStu: String?,
    val priTch: String?,
    val priStf: String?,
    val priStu: String?,
    val priAvgTuit: String?
)

@Service
class Nis16b1MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_16b1"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for nis_16b1 import." }

        val records = file.inputStream.use { parseCsv(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
//                connection.prepareStatement("DELETE FROM nis_16b1").use { it.executeUpdate() }
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

    private fun parseCsv(inputStream: InputStream): List<Nis16b1Record> {
        val records = mutableListOf<Nis16b1Record>()

        InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 11) return emptyList()

            var currentGender = "BOTH"

            for (i in 11 until rows.size) {
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

                val year = cleanYear(col0) ?: continue

                records.add(
                    Nis16b1Record(
                        year = year,
                        gender = currentGender,
                        pubTch = cleanValue(row.getOrNull(1)),
                        pubStf = cleanValue(row.getOrNull(2)),
                        pubStu = cleanValue(row.getOrNull(3)),
                        priTch = cleanValue(row.getOrNull(4)),
                        priStf = cleanValue(row.getOrNull(5)),
                        priStu = cleanValue(row.getOrNull(6)),
                        priAvgTuit = cleanValue(row.getOrNull(7))
                    )
                )
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis16b1Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_16b1
            (year, gender, pub_tch, pub_stf, pub_stu, pri_tch, pri_stf, pri_stu, pri_avg_tuit, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, gender, upload_id) DO UPDATE SET
                pub_tch = EXCLUDED.pub_tch,
                pub_stf = EXCLUDED.pub_stf,
                pub_stu = EXCLUDED.pub_stu,
                pri_tch = EXCLUDED.pri_tch,
                pri_stf = EXCLUDED.pri_stf,
                pri_stu = EXCLUDED.pri_stu,
                pri_avg_tuit = EXCLUDED.pri_avg_tuit
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.year)
                stmt.setString(2, r.gender)
                stmt.setString(3, r.pubTch)
                stmt.setString(4, r.pubStf)
                stmt.setString(5, r.pubStu)
                stmt.setString(6, r.priTch)
                stmt.setString(7, r.priStf)
                stmt.setString(8, r.priStu)
                stmt.setString(9, r.priAvgTuit)
                stmt.setString(10, uploadId)
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

    private fun cleanYear(value: String?): String? {
        return value?.trim()?.replace(" ", "")?.toLatinDigits()
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