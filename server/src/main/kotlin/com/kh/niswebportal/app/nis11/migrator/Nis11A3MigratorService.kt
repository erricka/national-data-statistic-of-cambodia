package com.kh.niswebportal.app.nis11.migrator

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

data class Nis11A3CompanyRecord(
    val year: Int,
    val companyName: String,
    val companyType: String,
    val annualIncome: Double?,
    val annualExpense: Double?,
    val phone: String
)

@Service
class Nis11A3MigratorService(
    private val dataSource: DataSource
) : NisMigratorStrategy {

    override val sourceId: String = "nis_11a3"

    @Transactional
    override fun processAndMigrate(file: MultipartFile, uploadId: String): Int {
        val fileName = file.originalFilename?.lowercase() ?: ""
        require(fileName.endsWith(".csv")) { "Expected CSV file format for 11a3 import." }

        val records = file.inputStream.use { parseCsv11A3(it) }

        if (records.isNotEmpty()) {
            dataSource.connection.use { insertBatch(it, records, uploadId) }
        }

        return records.size
    }

    private fun parseCsv11A3(inputStream: InputStream): List<Nis11A3CompanyRecord> {
        val records = mutableListOf<Nis11A3CompanyRecord>()

        InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
            val parser = CSVParser(reader, CSVFormat.DEFAULT)
            val rows = parser.records

            if (rows.size < 4) return emptyList()

            // 1. Extract the reporting year from the Title in Row 0 (e.g., "ឆ្នាំ២០២៥")
            var currentYear = 2025 // Safe default
            val titleRow = rows[0].getOrNull(1)?.trim() ?: ""
            val yearMatch = Regex("ឆ្នាំ\\s*([០-៩0-9]{4})").find(titleRow)

            if (yearMatch != null) {
                val parsedYear = parseKhmerInt(yearMatch.groupValues[1])
                if (parsedYear != null) {
                    currentYear = parsedYear
                }
            }

            // 2. Data starts at Row 3 (Skipping headers and the 11A3_x metadata variable row)
            for (i in 3 until rows.size) {
                val row = rows[i]
                val companyName = row.getOrNull(0)?.trim() ?: ""
                val companyType = row.getOrNull(1)?.trim() ?: ""

                // Stop if the row is empty or a placeholder
                if (companyName.isBlank() || companyName == "-") continue

                records.add(
                    Nis11A3CompanyRecord(
                        year = currentYear,
                        companyName = companyName,
                        companyType = companyType,
                        annualIncome = KhmerStringUtils.parseKhmerDouble(row.getOrNull(2)),
                        annualExpense = KhmerStringUtils.parseKhmerDouble(row.getOrNull(3)),
                        phone = row.getOrNull(4)?.trim() ?: ""
                    )
                )
            }
        }
        return records
    }

    private fun insertBatch(connection: Connection, records: List<Nis11A3CompanyRecord>, uploadId: String) {
        val sql = """
            INSERT INTO nis_11a3_press_companies 
            (year, company_name, company_type, annual_income, annual_expense, phone, upload_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (year, company_name, upload_id) DO UPDATE SET
                company_type = EXCLUDED.company_type,
                annual_income = EXCLUDED.annual_income,
                annual_expense = EXCLUDED.annual_expense,
                phone = EXCLUDED.phone
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            for (r in records) {
                stmt.setInt(1, r.year)
                stmt.setString(2, r.companyName)
                stmt.setString(3, r.companyType)
                stmt.setNullableDouble( 4, r.annualIncome)
                stmt.setNullableDouble( 5, r.annualExpense)
                stmt.setString(6, r.phone)
                stmt.setString(7, uploadId)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
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