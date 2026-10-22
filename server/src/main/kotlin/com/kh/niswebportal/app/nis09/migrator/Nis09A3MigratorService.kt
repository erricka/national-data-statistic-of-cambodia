package com.kh.niswebportal.app.nis09.migrator

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
import javax.sql.DataSource

data class Nis09A3AgencyRecord(
    val itemCode: String,
    val agencyName: String,
    val acronym: String,
    val estHq: String,
    val representative: String,
    val poc: String,
    val address: String
)

@Service
class Nis09A3MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_09a3"

    // Helper class to accumulate multi-row data before building the final record
    private class RecordBuilder(val itemCode: String) {
        val agencyName = mutableListOf<String>()
        val acronym = mutableListOf<String>()
        val estHq = mutableListOf<String>()
        val representative = mutableListOf<String>()
        val poc = mutableListOf<String>()
        val address = mutableListOf<String>()

        fun build() = Nis09A3AgencyRecord(
            itemCode = itemCode,
            agencyName = agencyName.joinToString("\n"),
            acronym = acronym.joinToString(" "),
            estHq = estHq.joinToString(" "),
            representative = representative.joinToString("\n"),
            poc = poc.joinToString("\n"),
            address = address.joinToString("\n")
        )
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 09a3 import." }

        val records = file.inputStream.use { parseCsv09A3(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv09A3(inputStream: InputStream): List<Nis09A3AgencyRecord> {
        val records = mutableListOf<Nis09A3AgencyRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 5) return emptyList()

            var currentRecord: RecordBuilder? = null

            // Data starts at row index 5
            for (i in 5 until rows.size) {
                val row = rows[i]
                val col0 = row.getOrNull(0)?.trim() ?: ""
                val col1 = row.getOrNull(1)?.trim() ?: ""
                val col2 = row.getOrNull(2)?.trim() ?: ""
                val col3 = row.getOrNull(3)?.trim() ?: ""
                val col4 = row.getOrNull(4)?.trim() ?: ""
                val col5 = row.getOrNull(5)?.trim() ?: ""
                val col6 = row.getOrNull(6)?.trim() ?: ""

                // 1. Detect start of a new UN Agency Record cleanly via item code
                if (col0.startsWith("09A3_")) {
                    // Save the previous accumulated record if it exists
                    currentRecord?.let { records.add(it.build()) }

                    // Initialize the new record
                    currentRecord = RecordBuilder(itemCode = col0).apply {
                        if (col1.isNotEmpty()) agencyName.add(col1)
                        if (col2.isNotEmpty()) acronym.add(col2)
                        if (col3.isNotEmpty()) estHq.add(col3)
                        if (col4.isNotEmpty()) representative.add(col4)
                        if (col5.isNotEmpty()) poc.add(col5)
                        if (col6.isNotEmpty()) address.add(col6)
                    }
                }
                // 2. Accumulate supplementary rows belonging to the current agency
                else if (currentRecord != null) {
                    if (col1.isNotEmpty()) currentRecord.agencyName.add(col1)
                    if (col2.isNotEmpty()) currentRecord.acronym.add(col2)
                    if (col3.isNotEmpty()) currentRecord.estHq.add(col3)
                    if (col4.isNotEmpty()) currentRecord.representative.add(col4)
                    if (col5.isNotEmpty()) currentRecord.poc.add(col5)
                    if (col6.isNotEmpty()) currentRecord.address.add(col6)
                }
            }
            // Add the very last accumulated record to the list
            currentRecord?.let { records.add(it.build()) }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis09A3AgencyRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_09a3_un_agencies 
            (item_code, agency_name, acronym, est_hq, representative, poc, address, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (item_code, upload_id) DO UPDATE SET
                agency_name = EXCLUDED.agency_name,
                acronym = EXCLUDED.acronym,
                est_hq = EXCLUDED.est_hq,
                representative = EXCLUDED.representative,
                poc = EXCLUDED.poc,
                address = EXCLUDED.address
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.itemCode)
                stmt.setString(2, r.agencyName)
                stmt.setString(3, r.acronym)
                stmt.setString(4, r.estHq)
                stmt.setString(5, r.representative)
                stmt.setString(6, r.poc)
                stmt.setString(7, r.address)
                stmt.setString(8, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    
}