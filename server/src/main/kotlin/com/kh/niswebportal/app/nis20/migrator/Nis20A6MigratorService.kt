package com.kh.niswebportal.app.nis20.migrator

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

data class Nis20A6CreditRecord(
    val provinceName: String,
    val year: Int,
    val totalCashLoan: Double?,
    val totalBorrowers: Int?,
    val cashInHand: Double?,
    val yearlyBorrowers: Int?
)

@Service
class Nis20A6MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_20a6"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 20a6 import." }

        val records = file.inputStream.use { parseCsv20A6(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv20A6(inputStream: InputStream): List<Nis20A6CreditRecord> {
        val records = mutableListOf<Nis20A6CreditRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 6) return emptyList()

            // 1. Scan Row 2 (index 2) to dynamically map column indexes to Years
            val yearMappings = mutableListOf<Pair<Int, Int>>() // Pair<Year, ColumnIndex>
            val yearRow = rows[2]

            // Yearly data starts at column index 4 and increments by 2 (Cash in Hand, Borrowers)
            for (c in 4 until yearRow.size() step 2) {
                val yearText = yearRow.getOrNull(c)?.trim() ?: ""
                val year = parseKhmerInt(yearText)
                if (year != null && year > 2000) { // Safety check to ensure it's a valid year like 2011
                    yearMappings.add(Pair(year, c))
                }
            }

            // 2. Extract Data (Provinces start at Row 5 in CSV/Excel)
            for (i in 5 until rows.size) {
                val row = rows[i]
                val province = row.getOrNull(1)?.trim() ?: ""

                if (province.isBlank() || province == "Pro_name") continue

                // A. Extract Overall Totals (Stored as year = 0 in column indexes 2 and 3)
                records.add(
                    Nis20A6CreditRecord(
                        provinceName = province,
                        year = 0,
                        totalCashLoan = KhmerStringUtils.parseKhmerDouble(row.getOrNull(2)),
                        totalBorrowers = parseKhmerInt(row.getOrNull(3)),
                        cashInHand = null,
                        yearlyBorrowers = null
                    )
                )

                // B. Extract Chronological Year Breakdowns
                for ((year, colIndex) in yearMappings) {
                    records.add(
                        Nis20A6CreditRecord(
                            provinceName = province,
                            year = year,
                            totalCashLoan = null,
                            totalBorrowers = null,
                            cashInHand = KhmerStringUtils.parseKhmerDouble(row.getOrNull(colIndex)),
                            yearlyBorrowers = parseKhmerInt(row.getOrNull(colIndex + 1))
                        )
                    )
                }
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis20A6CreditRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_20a6_rural_credit 
            (province_name, year, total_cash_loan, total_borrowers, cash_in_hand, yearly_borrowers, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (province_name, year, upload_id) DO UPDATE SET
                total_cash_loan = EXCLUDED.total_cash_loan,
                total_borrowers = EXCLUDED.total_borrowers,
                cash_in_hand = EXCLUDED.cash_in_hand,
                yearly_borrowers = EXCLUDED.yearly_borrowers
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setString(1, r.provinceName)
                stmt.setInt(2, r.year)
                stmt.setNullableDouble( 3, r.totalCashLoan)
                setNullableInt(stmt, 4, r.totalBorrowers)
                stmt.setNullableDouble( 5, r.cashInHand)
                setNullableInt(stmt, 6, r.yearlyBorrowers)
                stmt.setString(7, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun setNullableInt(stmt: PreparedStatement, index: Int, value: Int?) {
        if (value != null) stmt.setInt(index, value) else stmt.setNull(index, Types.INTEGER)
    }

    

    private fun parseKhmerInt(text: String?): Int? {
        if (text.isNullOrBlank() || text.trim() == "-") return null
        val khmerToLatinDigits = mapOf(
            '០' to '0', '១' to '1', '២' to '2', '៣' to '3', '៤' to '4',
            '៥' to '5', '៦' to '6', '៧' to '7', '៨' to '8', '៩' to '9'
        )
        val normalized = text.map { khmerToLatinDigits[it] ?: it }.joinToString("")
        val cleanString = normalized.replace(Regex("[^0-9]"), "")
        return cleanString.toIntOrNull()
    }

    

    
}