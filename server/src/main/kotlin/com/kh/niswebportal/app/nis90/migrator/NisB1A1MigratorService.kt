package com.kh.niswebportal.app.nisb1.migrator

import com.kh.niswebportal.core.util.KhmerStringUtils
import com.kh.niswebportal.core.util.getOrNull
import com.kh.niswebportal.core.util.setNullableDouble
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

data class NisB1A1ExportRecord(
    val productName: String,
    val year: Int,
    val month: Int,
    val totalExport: Double?
)

@Service
class NisB1A1MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_b1a1"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for B1A1 import." }

        val records = file.inputStream.use { parseCsvB1A1(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsvB1A1(inputStream: InputStream): List<NisB1A1ExportRecord> {
        val records = mutableListOf<NisB1A1ExportRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            // Skip Row 0 (Title) and Row 1 (Headers). Data starts at Row 2.
            for (i in 2 until rows.size) {
                val row = rows[i]

                val productName = row.getOrNull(0)?.trim() ?: ""
                val yearStr = row.getOrNull(1)?.trim() ?: ""
                val monthStr = row.getOrNull(2)?.trim() ?: ""

                if (productName.isBlank() || yearStr.isBlank() || monthStr.isBlank()) continue

                val year = yearStr.toIntOrNull()
                val month = monthStr.toIntOrNull()

                // Use the robust parser to strip commas, currency symbols, and spaces
                val totalExport = KhmerStringUtils.parseKhmerDouble(row.getOrNull(3))

                if (year != null && month != null) {
                    records.add(
                        NisB1A1ExportRecord(
                            productName = productName,
                            year = year,
                            month = month,
                            totalExport = totalExport
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<NisB1A1ExportRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_b1a1_exports 
            (product_name, year, month, total_export, upload_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (product_name, year, month, upload_id) DO UPDATE SET
                total_export = EXCLUDED.total_export
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.productName)
                stmt.setInt(2, r.year)
                stmt.setInt(3, r.month)
                stmt.setNullableDouble( 4, r.totalExport)
                stmt.setString(5, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    

    // Advanced Parser to strip commas and handle formatting
    

    
}