package com.kh.niswebportal.app.nis22.migrator

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

data class Nis22A2Record(
    val year: Int,
    val numMobilePhones: Long?,
    val domsConversationsKMin: Long?,
    val interConversationsSent: Long?,
    val interConversationsRecv: Long?
)

@Service
class Nis22A2MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_22a2"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 22a2 import." }

        val records = file.inputStream.use { parseCsv22A2(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv22A2(inputStream: InputStream): List<Nis22A2Record> {
        val records = mutableListOf<Nis22A2Record>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.isEmpty()) return emptyList()

            // 1. Locate technical header row containing '22a2_year'
            var headerRowIndex = -1
            for (i in rows.indices) {
                if (rows[i].getOrNull(1)?.trim() == "22a2_year") {
                    headerRowIndex = i
                    break
                }
            }

            val startIndex = if (headerRowIndex != -1) headerRowIndex + 1 else 6

            // 2. Parse yearly records row by row
            for (i in startIndex until rows.size) {
                val row = rows[i]
                if (row.size() < 2) continue

                val yearText = row.getOrNull(1)?.trim()
                if (yearText.isNullOrBlank() || yearText.startsWith("Source:", ignoreCase = true)) continue

                val year = parseKhmerLong(yearText)?.toInt() ?: continue
                if (year !in 1900..2099) continue

                val numMobilePhones = parseKhmerLong(row.getOrNull(2))
                val domsConversationsKMin = parseKhmerLong(row.getOrNull(3))
                val interConversationsSent = parseKhmerLong(row.getOrNull(4))
                val interConversationsRecv = parseKhmerLong(row.getOrNull(5))

                records.add(
                    Nis22A2Record(
                        year = year,
                        numMobilePhones = numMobilePhones,
                        domsConversationsKMin = domsConversationsKMin,
                        interConversationsSent = interConversationsSent,
                        interConversationsRecv = interConversationsRecv
                    )
                )
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis22A2Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_22a2_telecommunication_stats 
            (year, num_mobile_phones, doms_conversations_k_min, inter_conversations_sent, inter_conversations_recv, upload_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, upload_id) DO UPDATE SET
                num_mobile_phones = EXCLUDED.num_mobile_phones,
                doms_conversations_k_min = EXCLUDED.doms_conversations_k_min,
                inter_conversations_sent = EXCLUDED.inter_conversations_sent,
                inter_conversations_recv = EXCLUDED.inter_conversations_recv
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setInt(1, r.year)
                setNullableLong(stmt, 2, r.numMobilePhones)
                setNullableLong(stmt, 3, r.domsConversationsKMin)
                setNullableLong(stmt, 4, r.interConversationsSent)
                setNullableLong(stmt, 5, r.interConversationsRecv)
                stmt.setString(6, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun setNullableLong(stmt: PreparedStatement, index: Int, value: Long?) {
        if (value != null) stmt.setLong(index, value) else stmt.setNull(index, Types.BIGINT)
    }

    private fun parseKhmerLong(text: String?): Long? {
        if (text.isNullOrBlank() || text.trim() == "-") return null
        val khmerToLatinDigits = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
        val normalized = text.map { khmerToLatinDigits[it] ?: it }.joinToString("")
        val digitsOnly = normalized.replace(Regex("[^0-9]"), "")
        return digitsOnly.toLongOrNull()
    }

    
}