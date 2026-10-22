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

data class Nis16b2Record(
    val institutionCode: String,
    val institutionName: String,
    val sector: String,
    val yr1314Total: String?, val yr1314F: String?,
    val yr1415Total: String?, val yr1415F: String?,
    val yr1516Total: String?, val yr1516F: String?,
    val yr1617Total: String?, val yr1617F: String?,
    val yr1718Total: String?, val yr1718F: String?,
    val yr1819Total: String?, val yr1819F: String?,
    val yr1920Total: String?, val yr1920F: String?,
    val yr2021Total: String?, val yr2021F: String?,
    val yr2122Total: String?, val yr2122F: String?,
    val yr2223Total: String?, val yr2223F: String?,
    val yr2324Total: String?, val yr2324F: String?
)

@Service
class Nis16b2MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_16b2"

    private companion object {
        private const val BATCH_SIZE = 500
        private val KHMER_TO_LATIN_MAP = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for nis_16b2 import." }

        val records = file.inputStream.use { parseCsv(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
//                connection.prepareStatement("DELETE FROM nis_16b2").use { it.executeUpdate() }
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

    private fun parseCsv(inputStream: InputStream): List<Nis16b2Record> {
        val records = mutableListOf<Nis16b2Record>()

        InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 11) return emptyList()

            var currentSector = "PUBLIC"

            for (i in 11 until rows.size) {
                val row = rows[i]
                val col0 = row.getOrNull(0)?.trim() ?: ""
                val col1 = row.getOrNull(1)?.trim() ?: ""

                if (col0.isBlank() || col0.lowercase() == "nan") continue

                if (col0.contains("16b2_pub") || col0.contains("រដ្ឋ")) {
                    currentSector = "PUBLIC"
                    continue
                } else if (col0.contains("16b2_pri") || col0.contains("ឯកជន")) {
                    currentSector = "PRIVATE"
                    continue
                } else if (col0.contains("ប្រភព") || col0.contains("Source")) {
                    break
                }

                records.add(
                    Nis16b2Record(
                        institutionCode = col0,
                        institutionName = col1,
                        sector = currentSector,
                        yr1314Total = cleanValue(row.getOrNull(2)),
                        yr1314F = cleanValue(row.getOrNull(3)),
                        yr1415Total = cleanValue(row.getOrNull(4)),
                        yr1415F = cleanValue(row.getOrNull(5)),
                        yr1516Total = cleanValue(row.getOrNull(6)),
                        yr1516F = cleanValue(row.getOrNull(7)),
                        yr1617Total = cleanValue(row.getOrNull(8)),
                        yr1617F = cleanValue(row.getOrNull(9)),
                        yr1718Total = cleanValue(row.getOrNull(10)),
                        yr1718F = cleanValue(row.getOrNull(11)),
                        yr1819Total = cleanValue(row.getOrNull(12)),
                        yr1819F = cleanValue(row.getOrNull(13)),
                        yr1920Total = cleanValue(row.getOrNull(14)),
                        yr1920F = cleanValue(row.getOrNull(15)),
                        yr2021Total = cleanValue(row.getOrNull(16)),
                        yr2021F = cleanValue(row.getOrNull(17)),
                        yr2122Total = cleanValue(row.getOrNull(18)),
                        yr2122F = cleanValue(row.getOrNull(19)),
                        yr2223Total = cleanValue(row.getOrNull(20)),
                        yr2223F = cleanValue(row.getOrNull(21)),
                        yr2324Total = cleanValue(row.getOrNull(22)),
                        yr2324F = cleanValue(row.getOrNull(23))
                    )
                )
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis16b2Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_16b2
            (institution_code, institution_name, sector, 
             yr_13_14_total, yr_13_14_f, yr_14_15_total, yr_14_15_f, 
             yr_15_16_total, yr_15_16_f, yr_16_17_total, yr_16_17_f, 
             yr_17_18_total, yr_17_18_f, yr_18_19_total, yr_18_19_f, 
             yr_19_20_total, yr_19_20_f, yr_20_21_total, yr_20_21_f, 
             yr_21_22_total, yr_21_22_f, yr_22_23_total, yr_22_23_f, 
             yr_23_24_total, yr_23_24_f, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (institution_code, upload_id) DO UPDATE SET
                institution_name = EXCLUDED.institution_name,
                sector = EXCLUDED.sector,
                yr_13_14_total = EXCLUDED.yr_13_14_total, yr_13_14_f = EXCLUDED.yr_13_14_f,
                yr_14_15_total = EXCLUDED.yr_14_15_total, yr_14_15_f = EXCLUDED.yr_14_15_f,
                yr_15_16_total = EXCLUDED.yr_15_16_total, yr_15_16_f = EXCLUDED.yr_15_16_f,
                yr_16_17_total = EXCLUDED.yr_16_17_total, yr_16_17_f = EXCLUDED.yr_16_17_f,
                yr_17_18_total = EXCLUDED.yr_17_18_total, yr_17_18_f = EXCLUDED.yr_17_18_f,
                yr_18_19_total = EXCLUDED.yr_18_19_total, yr_18_19_f = EXCLUDED.yr_18_19_f,
                yr_19_20_total = EXCLUDED.yr_19_20_total, yr_19_20_f = EXCLUDED.yr_19_20_f,
                yr_20_21_total = EXCLUDED.yr_20_21_total, yr_20_21_f = EXCLUDED.yr_20_21_f,
                yr_21_22_total = EXCLUDED.yr_21_22_total, yr_21_22_f = EXCLUDED.yr_21_22_f,
                yr_22_23_total = EXCLUDED.yr_22_23_total, yr_22_23_f = EXCLUDED.yr_22_23_f,
                yr_23_24_total = EXCLUDED.yr_23_24_total, yr_23_24_f = EXCLUDED.yr_23_24_f
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.institutionCode)
                stmt.setString(2, r.institutionName)
                stmt.setString(3, r.sector)
                stmt.setString(4, r.yr1314Total); stmt.setString(5, r.yr1314F)
                stmt.setString(6, r.yr1415Total); stmt.setString(7, r.yr1415F)
                stmt.setString(8, r.yr1516Total); stmt.setString(9, r.yr1516F)
                stmt.setString(10, r.yr1617Total); stmt.setString(11, r.yr1617F)
                stmt.setString(12, r.yr1718Total); stmt.setString(13, r.yr1718F)
                stmt.setString(14, r.yr1819Total); stmt.setString(15, r.yr1819F)
                stmt.setString(16, r.yr1920Total); stmt.setString(17, r.yr1920F)
                stmt.setString(18, r.yr2021Total); stmt.setString(19, r.yr2021F)
                stmt.setString(20, r.yr2122Total); stmt.setString(21, r.yr2122F)
                stmt.setString(22, r.yr2223Total); stmt.setString(23, r.yr2223F)
                stmt.setString(24, r.yr2324Total); stmt.setString(25, r.yr2324F)
                stmt.setString(26, uploadId)
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