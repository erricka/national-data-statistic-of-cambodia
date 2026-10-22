package com.kh.niswebportal.app.nis24.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

data class EconomyHealthItem(
    val itemCode: String,
    val description: String
)

@Service
class Nis24A2InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_24a2"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
// Sort dynamically by the initial insertion order (id) to keep columns ordered left-to-right as in Excel
            val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY item_code) as item_order,
                    item_code,
                    description,
                    year,
                    count_value
                FROM nis_24a2_eco_health_edu_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY year ASC, item_order ASC
            """.trimIndent()

            val orderedItems = mutableListOf<EconomyHealthItem>()
            val dataMap = mutableMapOf<Int, MutableMap<String, Double?>>() // Map<Year, Map<ItemCode, Value>>
            val allYears = mutableSetOf<Int>()
            val seenItems = mutableSetOf<String>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val itemCode = rs.getString("item_code")
                        val description = rs.getString("description")
                        val year = rs.getInt("year")

                        if (!seenItems.contains(itemCode)) {
                            seenItems.add(itemCode)
                            orderedItems.add(EconomyHealthItem(itemCode, description))
                        }

                        allYears.add(year)

                        if (!dataMap.containsKey(year)) {
                            dataMap[year] = mutableMapOf()
                        }

                        dataMap[year]!![itemCode] = rs.getNullableDouble("count_value")
                    }
                }
            }

            val sortedYears = allYears.sorted()

            // 1. Construct Flat Headers
            val headerRow = mutableListOf<HeaderItem>()
            headerRow.add(HeaderItem(title = "ឆ្នាំ")) // Year Column Header

            // Append Category Descriptions as Columns dynamically
            for (item in orderedItems) {
                headerRow.add(HeaderItem(title = item.description))
            }

            // 2. Construct Pivot Data Rows (Years as Rows)
            val tbRows = mutableListOf<List<Any?>>()

            for (year in sortedYears) {
                val row = mutableListOf<Any?>()
                row.add(year) // First column is the Year

                // Append corresponding category values left-to-right
                for (item in orderedItems) {
                    val value = dataMap[year]?.get(item.itemCode)
                    row.add(value)
                }

                tbRows.add(row)
            }

            return DynamicReportResponse(
                title = "ស្ថិតិយេនឌ័រ", // Gender Statistics
                sub_title = "24A2 ស្ថិតិស្រីក្នុងវិស័យសេដ្ឋកិច្ច សុខាភិបាល និងអប់រំ", // Women in Economy, Health, and Education
                tb_headers = listOf(headerRow),
                tb_rows = tbRows
            )
        }
    }

    
}