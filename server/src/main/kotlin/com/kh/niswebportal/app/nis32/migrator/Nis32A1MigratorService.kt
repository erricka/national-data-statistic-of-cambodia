package com.kh.niswebportal.app.nis32.migrator

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

data class Nis32A1AirCargoTransportRecord(
    val year: Int,
    val categoryCode: String,
    val categoryNameKh: String,
    val unit: String = "Ton",
    val importDomestic: Double = 0.0,
    val exportDomestic: Double = 0.0,
    val importInternational: Double = 0.0,
    val exportInternational: Double = 0.0,
    val totalAmount: Double = 0.0
)

@Service
class Nis32A1MigratorService(
    private val dataSource: DataSource
): NisMigratorStrategy {

    override val sourceId: String = "nis_32a1"

    private companion object {
        private const val BATCH_SIZE = 1000
    }

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 32A1 import." }

        val records = file.inputStream.use { inputStream ->
            parseCsv32A1(inputStream)
        }

        if (records.isNotEmpty()) {
            dataSource.connection.use { connection ->
                insertBatch(connection, records, uploadId)
            }
        }

        return records.size
    }

    private fun parseCsv32A1(inputStream: InputStream): List<Nis32A1AirCargoTransportRecord> {
        val records = mutableListOf<Nis32A1AirCargoTransportRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 17) return emptyList()

            val yearHeaderRow = rows[16]
            val yearColMap = mutableMapOf<Int, Int>()

            for (colIdx in 2 until yearHeaderRow.size()) {
                val headerVal = KhmerStringUtils.convertKhmerNumerals(yearHeaderRow.getOrNull(colIdx) ?: "")
                val yearMatch = Regex("\\d{4}").find(headerVal)
                if (yearMatch != null) {
                    yearColMap[colIdx] = yearMatch.value.toInt()
                }
            }

            for (rowIndex in 17 until rows.size) {
                val row = rows[rowIndex]
                if (row.size() < 3) continue

                val code = row.getOrNull(0)?.trim() ?: ""
                val categoryName = row.getOrNull(1)?.trim() ?: ""

                if (code.isBlank() || categoryName.contains("សរុប")) continue

                val categoryCode = when {
                    code.isNotBlank() -> code
                    categoryName.contains("ក្នុងស្រុក") -> "32A1_1"
                    categoryName.contains("ក្រៅស្រុក") -> "32A1_2"
                    else -> "32A1_OTHER"
                }

                yearColMap.forEach { (colIdx, year) ->
                    val totalTon = KhmerStringUtils.parseKhmerDouble(row.getOrNull(colIdx)) ?: 0.0
                    records.add(
                        Nis32A1AirCargoTransportRecord(
                            year = year,
                            categoryCode = categoryCode,
                            categoryNameKh = categoryName,
                            unit = "Ton",
                            totalAmount = totalTon
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis32A1AirCargoTransportRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_32a1_air_cargo_transport
            (year, category_code, category_name_kh, unit, import_domestic, export_domestic, import_international, export_international, total_amount, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, category_code, upload_id) DO UPDATE SET
                category_name_kh = EXCLUDED.category_name_kh,
                unit = EXCLUDED.unit,
                import_domestic = EXCLUDED.import_domestic,
                export_domestic = EXCLUDED.export_domestic,
                import_international = EXCLUDED.import_international,
                export_international = EXCLUDED.export_international,
                total_amount = EXCLUDED.total_amount
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            var count = 0
            for (r in records) {
                stmt.setInt(1, r.year)
                stmt.setString(2, r.categoryCode)
                stmt.setString(3, r.categoryNameKh)
                stmt.setString(4, r.unit)
                stmt.setDouble(5, r.importDomestic)
                stmt.setDouble(6, r.exportDomestic)
                stmt.setDouble(7, r.importInternational)
                stmt.setDouble(8, r.exportInternational)
                stmt.setDouble(9, r.totalAmount)
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
}
