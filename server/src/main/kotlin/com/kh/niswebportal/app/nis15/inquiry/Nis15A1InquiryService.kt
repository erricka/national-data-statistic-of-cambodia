package com.kh.niswebportal.app.nis15.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

data class RegistrationData(
    val serialNo: String,
    val description: String,
    val yearlyCounts: MutableMap<Int, Double?> = mutableMapOf()
)

@Service
class Nis15A1InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_15a1"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY category_name) as cat_order,
                    MIN(id) OVER (PARTITION BY item_code) as item_order,
                    category_name,
                    item_code,
                    serial_no,
                    description,
                    year,
                    count_value
                FROM nis_15a1_business_registration
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY cat_order ASC, item_order ASC, year ASC
            """.trimIndent()

            val categoryMap = mutableMapOf<String, MutableMap<String, RegistrationData>>()
            val orderedCategories = mutableListOf<String>()
            val orderedItems = mutableMapOf<String, MutableList<String>>() // Cat -> List<ItemCode>
            val allYears = mutableSetOf<Int>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val category = rs.getString("category_name")
                        val itemCode = rs.getString("item_code")
                        val year = rs.getInt("year")

                        if (!categoryMap.containsKey(category)) {
                            orderedCategories.add(category)
                            orderedItems[category] = mutableListOf()
                            categoryMap[category] = mutableMapOf()
                        }

                        if (!categoryMap[category]!!.containsKey(itemCode)) {
                            orderedItems[category]!!.add(itemCode)
                            categoryMap[category]!![itemCode] = RegistrationData(
                                serialNo = rs.getString("serial_no") ?: "",
                                description = rs.getString("description") ?: ""
                            )
                        }

                        allYears.add(year)
                        categoryMap[category]!![itemCode]!!.yearlyCounts[year] = rs.getNullableDouble("count_value")
                    }
                }
            }

            val sortedYears = allYears.sorted()

            // 1. Construct Flat Headers
            val headerRow = mutableListOf<HeaderItem>()
            headerRow.add(HeaderItem(title = "ល.រ"))
            headerRow.add(HeaderItem(title = "កូដ"))
            headerRow.add(HeaderItem(title = "យថាប្រភេទ"))

            // Dynamically add Year columns to the header
            for (year in sortedYears) {
                headerRow.add(HeaderItem(title = year.toString()))
            }

            // 2. Construct Pivot Data Rows
            val tbRows = mutableListOf<List<Any?>>()

            for (category in orderedCategories) {
                // Inject the Category Grouping Row
                val catRow = mutableListOf<Any?>(category, "", "")
                for (year in sortedYears) catRow.add("") // Pad the rest of the columns
                tbRows.add(catRow)

                // Inject the actual Data Rows
                for (itemCode in orderedItems[category]!!) {
                    val row = mutableListOf<Any?>()
                    val data = categoryMap[category]!![itemCode]!!

                    row.add(data.serialNo)
                    row.add(itemCode)
                    row.add(data.description)

                    // Append each year's data dynamically
                    for (year in sortedYears) {
                        row.add(data.yearlyCounts[year])
                    }

                    tbRows.add(row)
                }
            }

            return DynamicReportResponse(
                title = "ក្រសួងពាណិជ្ជកម្ម", // Ministry of Commerce
                sub_title = "15A1 ស្ថិតិការចុះបញ្ជីពាណិជ្ជកម្មប្រចាំឆ្នាំ", // Business Registration Statistics
                tb_headers = listOf(headerRow),
                tb_rows = tbRows
            )
        }
    }

    
}