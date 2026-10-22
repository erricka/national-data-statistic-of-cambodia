package com.kh.niswebportal.app.nis25.migrator

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
import javax.sql.DataSource

data class Nis25C4RoadAccidentRecord(
    val provinceCode: String,
    val provinceKh: String,
    val year: String,
    val injDthTotal: String?,
    val injDthF: String?,
    val injSinjTotal: String?,
    val injSinjF: String?,
    val injMinjTotal: String?,
    val injMinjF: String?,
    val caseSpeed: String?,
    val caseNyld: String?,
    val caseNkr: String?,
    val caseUtrn: String?,
    val caseAlc: String?,
    val caseOvtk: String?,
    val caseDrow: String?,
    val caseDrug: String?,
    val caseHlth: String?,
    val caseVeh: String?,
    val caseRd: String?,
    val caseWthr: String?,
    val caseOth: String?,
    val caseTotal: String?
)

@Service
class Nis25C4MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_25c4"

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
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 25C4 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv25C4(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv25C4(inputStream: InputStream): List<Nis25C4RoadAccidentRecord> {
        val records = mutableListOf<Nis25C4RoadAccidentRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size <= 12) return emptyList()

            var currentProvCode = ""
            var currentProvKh = ""

            for (i in 12 until rows.size) {
                val row = rows[i]

                val rawCode = row.getOrNull(0)?.trim() ?: ""
                val rawProvKh = row.getOrNull(1)?.trim() ?: ""
                val rawYear = row.getOrNull(2)?.trim()?.toLatinDigits()?.replace(",", "") ?: ""

                if (rawCode.isNotBlank() && rawCode != "nan") {
                    currentProvCode = rawCode
                    currentProvKh = rawProvKh
                }

                if (rawProvKh.contains("ប្រភព៖")) {
                    break
                }

                if (rawYear.matches(Regex("^\\d{4}$")) && currentProvCode.isNotBlank()) {
                    records.add(
                        Nis25C4RoadAccidentRecord(
                            provinceCode = currentProvCode,
                            provinceKh = currentProvKh,
                            year = rawYear,
                            injDthTotal = cleanValue(row.getOrNull(3)),
                            injDthF = cleanValue(row.getOrNull(4)),
                            injSinjTotal = cleanValue(row.getOrNull(5)),
                            injSinjF = cleanValue(row.getOrNull(6)),
                            injMinjTotal = cleanValue(row.getOrNull(7)),
                            injMinjF = cleanValue(row.getOrNull(8)),
                            caseSpeed = cleanValue(row.getOrNull(9)),
                            caseNyld = cleanValue(row.getOrNull(10)),
                            caseNkr = cleanValue(row.getOrNull(11)),
                            caseUtrn = cleanValue(row.getOrNull(12)),
                            caseAlc = cleanValue(row.getOrNull(13)),
                            caseOvtk = cleanValue(row.getOrNull(14)),
                            caseDrow = cleanValue(row.getOrNull(15)),
                            caseDrug = cleanValue(row.getOrNull(16)),
                            caseHlth = cleanValue(row.getOrNull(17)),
                            caseVeh = cleanValue(row.getOrNull(18)),
                            caseRd = cleanValue(row.getOrNull(19)),
                            caseWthr = cleanValue(row.getOrNull(20)),
                            caseOth = cleanValue(row.getOrNull(21)),
                            caseTotal = cleanValue(row.getOrNull(22))
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis25C4RoadAccidentRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_25c4_road_accident_by_province_stats
            (
                province_code, province_kh, year,
                inj_dth_total, inj_dth_f, inj_sinj_total, inj_sinj_f, inj_minj_total, inj_minj_f,
                case_speed, case_nyld, case_nkr, case_utrn, case_alc, case_ovtk,
                case_drow, case_drug, case_hlth, case_veh, case_rd, case_wthr, case_oth, case_total,
                upload_id
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (province_code, year, upload_id) DO UPDATE SET
                province_kh = EXCLUDED.province_kh,
                inj_dth_total = EXCLUDED.inj_dth_total,
                inj_dth_f = EXCLUDED.inj_dth_f,
                inj_sinj_total = EXCLUDED.inj_sinj_total,
                inj_sinj_f = EXCLUDED.inj_sinj_f,
                inj_minj_total = EXCLUDED.inj_minj_total,
                inj_minj_f = EXCLUDED.inj_minj_f,
                case_speed = EXCLUDED.case_speed,
                case_nyld = EXCLUDED.case_nyld,
                case_nkr = EXCLUDED.case_nkr,
                case_utrn = EXCLUDED.case_utrn,
                case_alc = EXCLUDED.case_alc,
                case_ovtk = EXCLUDED.case_ovtk,
                case_drow = EXCLUDED.case_drow,
                case_drug = EXCLUDED.case_drug,
                case_hlth = EXCLUDED.case_hlth,
                case_veh = EXCLUDED.case_veh,
                case_rd = EXCLUDED.case_rd,
                case_wthr = EXCLUDED.case_wthr,
                case_oth = EXCLUDED.case_oth,
                case_total = EXCLUDED.case_total
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                var idx = 1
                stmt.setString(idx++, r.provinceCode)
                stmt.setString(idx++, r.provinceKh)
                stmt.setString(idx++, r.year)

                stmt.setString(idx++, r.injDthTotal)
                stmt.setString(idx++, r.injDthF)
                stmt.setString(idx++, r.injSinjTotal)
                stmt.setString(idx++, r.injSinjF)
                stmt.setString(idx++, r.injMinjTotal)
                stmt.setString(idx++, r.injMinjF)

                stmt.setString(idx++, r.caseSpeed)
                stmt.setString(idx++, r.caseNyld)
                stmt.setString(idx++, r.caseNkr)
                stmt.setString(idx++, r.caseUtrn)
                stmt.setString(idx++, r.caseAlc)
                stmt.setString(idx++, r.caseOvtk)
                stmt.setString(idx++, r.caseDrow)
                stmt.setString(idx++, r.caseDrug)
                stmt.setString(idx++, r.caseHlth)
                stmt.setString(idx++, r.caseVeh)
                stmt.setString(idx++, r.caseRd)
                stmt.setString(idx++, r.caseWthr)
                stmt.setString(idx++, r.caseOth)
                stmt.setString(idx++, r.caseTotal)

                stmt.setString(idx, uploadId)
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
        val cleaned = value?.trim()?.replace(",", "") ?: return null
        if (cleaned.isBlank() || cleaned == "-" || cleaned == "nan") return null
        return cleaned.toLatinDigits()
    }

    private fun String.toLatinDigits(): String {
        return this.map { KHMER_TO_LATIN_MAP[it] ?: it }.joinToString("")
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}