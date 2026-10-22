package com.kh.niswebportal.app.nisb1.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

data class ProductExportData(
    val productName: String,
    val monthlyData: MutableMap<Int, MutableMap<Int, Double?>> = mutableMapOf() // Map<Year, Map<Month, Value>>
)

@Service
class NisB1A1InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_b1a1"

    private val monthNames = mapOf(
        1 to "January", 2 to "February", 3 to "March", 4 to "April",
        5 to "May", 6 to "June", 7 to "July", 8 to "August",
        9 to "September", 10 to "October", 11 to "November", 12 to "December"
    )

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY product_name) as prod_order,
                    product_name,
                    year,
                    month,
                    total_export
                FROM nis_b1a1_exports
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY prod_order ASC, year ASC, month ASC
            """.trimIndent()

            val productMap = mutableMapOf<String, ProductExportData>()
            val orderedProducts = mutableListOf<String>()
            val yearMonths = mutableMapOf<Int, MutableSet<Int>>() // Tracks which months exist per year

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val productName = rs.getString("product_name")
                        val year = rs.getInt("year")
                        val month = rs.getInt("month")
                        val exportValue = rs.getNullableDouble("total_export")

                        if (!productMap.containsKey(productName)) {
                            orderedProducts.add(productName)
                            productMap[productName] = ProductExportData(productName)
                        }

                        val data = productMap[productName]!!
                        if (!data.monthlyData.containsKey(year)) {
                            data.monthlyData[year] = mutableMapOf()
                        }

                        // Register that this month exists for this year
                        yearMonths.getOrPut(year) { mutableSetOf() }.add(month)
                        data.monthlyData[year]!![month] = exportValue
                    }
                }
            }

            val sortedYears = yearMonths.keys.sorted()

            // 1. Construct 2-Tier Headers for Frontend Compatibility
            val topHeader = mutableListOf<HeaderItem>()
            val subHeader = mutableListOf<HeaderItem>()

            // Static First Column
            topHeader.add(HeaderItem(title = "\u200B")) // Visual blank space above "Product"
            subHeader.add(HeaderItem(title = "Product"))

            // Dynamically construct columns based ONLY on the months that exist for each year
            for (year in sortedYears) {
                val availableMonths = yearMonths[year]!!.sorted()
                val numMonths = availableMonths.size

                // Top Header: Year spans across available months, then Total
                topHeader.add(HeaderItem(title = year.toString()))
                for (i in 1 until numMonths) {
                    topHeader.add(HeaderItem(title = "\u200B"))
                }
                topHeader.add(HeaderItem(title = "$year Total"))

                // Sub Header: Month Names, then blank under Total
                for (month in availableMonths) {
                    subHeader.add(HeaderItem(title = monthNames[month] ?: ""))
                }
                subHeader.add(HeaderItem(title = "\u200B"))
            }

            // 2. Construct Pivot Data Rows
            val tbRows = mutableListOf<List<Any?>>()

            for (prodName in orderedProducts) {
                val data = productMap[prodName]!!
                val row = mutableListOf<Any?>()

                row.add(data.productName)

                // Append each year's monthly data and calculate total dynamically
                for (year in sortedYears) {
                    var yearlyTotal = 0.0
                    var hasDataForYear = false
                    val availableMonths = yearMonths[year]!!.sorted()

                    for (month in availableMonths) {
                        val value = data.monthlyData[year]?.get(month)
                        row.add(value)

                        if (value != null) {
                            yearlyTotal += value
                            hasDataForYear = true
                        }
                    }

                    // Add Yearly Total
                    if (hasDataForYear) {
                        row.add(yearlyTotal)
                    } else {
                        row.add(null)
                    }
                }

                tbRows.add(row)
            }

            return DynamicReportResponse(
                title = "Export Value",
                sub_title = "B1A1 Export value by main products",
                tb_headers = listOf(topHeader, subHeader), // Passing exactly 2 tiers prevents UI clipping
                tb_rows = tbRows
            )
        }
    }

    
}