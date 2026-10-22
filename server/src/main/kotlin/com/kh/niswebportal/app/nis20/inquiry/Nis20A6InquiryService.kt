package com.kh.niswebportal.app.nis20.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

data class CreditData(
    val totalCashLoan: Double?,
    val totalBorrowers: Int?,
    val cashInHand: Double?,
    val yearlyBorrowers: Int?
)

@Service
class Nis20A6InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_20a6"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY province_name) as prov_order,
                    province_name,
                    year,
                    total_cash_loan,
                    total_borrowers,
                    cash_in_hand,
                    yearly_borrowers
                FROM nis_20a6_rural_credit
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY prov_order ASC, year ASC
            """.trimIndent()

            val provMap = mutableMapOf<String, MutableMap<Int, CreditData>>()
            val orderedProvs = mutableListOf<String>()
            val allYears = mutableSetOf<Int>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val prov = rs.getString("province_name")
                        val year = rs.getInt("year")

                        if (!provMap.containsKey(prov)) {
                            orderedProvs.add(prov)
                            provMap[prov] = mutableMapOf()
                        }

                        if (year > 0) allYears.add(year)

                        provMap[prov]!![year] = CreditData(
                            totalCashLoan = rs.getNullableDouble("total_cash_loan"),
                            totalBorrowers = rs.getNullableInt("total_borrowers"),
                            cashInHand = rs.getNullableDouble("cash_in_hand"),
                            yearlyBorrowers = rs.getNullableInt("yearly_borrowers")
                        )
                    }
                }
            }

            val sortedYears = allYears.sorted()

            // 1. Construct 2-Tier Headers
            val topHeader = mutableListOf<HeaderItem>()
            val subHeader = mutableListOf<HeaderItem>()

            // Static Top Headers (Using \u200B trick for visual spacing)
            topHeader.add(HeaderItem(title = "\u200B")) // No.
            topHeader.add(HeaderItem(title = "\u200B")) // Province
            topHeader.add(HeaderItem(title = "ឆ្នាំអនុវត្ត")) // Parent for Total columns
            topHeader.add(HeaderItem(title = ""))       // Span for Total Borrowers

            // Static Sub Headers
            subHeader.add(HeaderItem(title = "ល.រ"))
            subHeader.add(HeaderItem(title = "ខេត្ត ក្រុង"))
            subHeader.add(HeaderItem(title = "ឥណទានសាច់ប្រាក់"))
            subHeader.add(HeaderItem(title = "ចំនួនអ្នកខ្ចី (នាក់)"))

            // Dynamic Yearly Headers
            for (year in sortedYears) {
                topHeader.add(HeaderItem(title = "ឆ្នាំ$year")) // Span above the year
                topHeader.add(HeaderItem(title = ""))       // Span

                subHeader.add(HeaderItem(title = "ចំនួនទឹកប្រាក់នៅក្នុងដៃអតិថិជន (រៀល)"))
                subHeader.add(HeaderItem(title = "ចំនួនអ្នកខ្ចី (នាក់)"))
            }

            // 2. Construct Pivot Data Rows
            val tbRows = mutableListOf<List<Any?>>()
            var serial = 1

            for (prov in orderedProvs) {
                val row = mutableListOf<Any?>()
                val provData = provMap[prov]!!
                val overallTotal = provData[0] // Year 0 represents the summary block

                row.add(serial++)
                row.add(prov)
                row.add(overallTotal?.totalCashLoan)
                row.add(overallTotal?.totalBorrowers)

                // Append each year dynamically
                for (year in sortedYears) {
                    val yearData = provData[year]
                    row.add(yearData?.cashInHand)
                    row.add(yearData?.yearlyBorrowers)
                }

                tbRows.add(row)
            }

            return DynamicReportResponse(
                title = "ក្រសួងអភិវឌ្ឍន៍ជនបទ", // Ministry of Rural Development
                sub_title = "20A6 ស្ថិតិឥណទានអភិវឌ្ឍន៍ជនបទ", // Rural Development Credit Statistics
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = tbRows
            )
        }
    }

    private fun ResultSet.getNullableInt(columnName: String): Int? {
        val value = this.getInt(columnName)
        return if (this.wasNull()) null else value
    }

    
}