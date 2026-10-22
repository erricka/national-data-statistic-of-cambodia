package com.kh.niswebportal.app.nis13.migrator

import com.kh.niswebportal.core.strategy.NisMigratorStrategy
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.sql.Connection
import javax.sql.DataSource

data class Nis13b4Record(
    val itemCode: String,
    val description: String,
    val unit: String?,
    val periodType: String?,
    val dataValue: String?,
    val remarks: String?
)

@Service
class Nis13b4MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_13b4"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 13 B4 import." }

        val records = mutableListOf<Nis13b4Record>()

        file.inputStream.use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val parser = CSVParser(reader, CSVFormat.DEFAULT)
                val rows = parser.records

                // Rows 10 to 31 contain the summary row (12) and various mineral products (12.1 through 12.21)
                for (i in 10..31) {
                    if (i >= rows.size) break
                    val row = rows[i]
                    val codeCol = row.getOrNull(0)?.trim() ?: ""
                    val descCol = row.getOrNull(1)?.trim() ?: ""

                    if (codeCol.isBlank() || codeCol.lowercase() == "nan" || codeCol == "សម្គាល់៖") {
                        continue
                    }

                    val record = Nis13b4Record(
                        itemCode = codeCol,
                        description = descCol,
                        unit = cleanValue(row.getOrNull(2)),
                        periodType = cleanValue(row.getOrNull(3)),
                        dataValue = cleanValue(row.getOrNull(4)),
                        remarks = cleanValue(row.getOrNull(5))
                    )

                    records.add(record)
                }

                if (records.isNotEmpty()) {
                    dataSource.connection.use { connection ->
                        insertBatch(connection, records, uploadId)
                    }
                }
            }
        }

        return records.size
    }

    private fun insertBatch(connection: Connection, records: List<Nis13b4Record>, uploadId: String) {
        val sql = """
            INSERT INTO nis_13b4_mineral_production_volume
            (item_code, description, unit, period_type, data_value, remarks, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setString(1, r.itemCode)
                stmt.setString(2, r.description)
                stmt.setString(3, r.unit)
                stmt.setString(4, r.periodType)
                stmt.setString(5, r.dataValue)
                stmt.setString(6, r.remarks)
                stmt.setString(7, uploadId)
                stmt.addBatch()

                count++
                if (count % BATCH_SIZE == 0) stmt.executeBatch()
            }
            if (count % BATCH_SIZE != 0) stmt.executeBatch()
        }
    }

    private fun cleanValue(value: String?): String? {
        val cleaned = value?.trim()?.replace(",", "") ?: return null
        if (cleaned.isBlank() || cleaned == "-" || cleaned == "nan" || cleaned == " - ") return null
        return cleaned
    }

    private fun CSVRecord.getOrNull(index: Int): String? {
        return if (index in 0 until this.size()) this.get(index) else null
    }
}