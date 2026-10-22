package com.kh.niswebportal.app.nis26.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

data class JusticeData(
    val itemCode: String,
    val department: String,
    val description: String,
    val yearlyCounts: MutableMap<Int, String?> = mutableMapOf()
)

data class JusticeBlock(
    val blockCode: String,
    val blockName: String,
    val items: MutableList<JusticeData> = mutableListOf()
)

@Service
class Nis26A3InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_26a3"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY block_code) as block_order,
                    MIN(id) OVER (PARTITION BY item_code) as item_order,
                    block_code,
                    block_name,
                    item_code,
                    department,
                    description,
                    year,
                    count_value
                FROM nis_26a3_justice_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY block_order ASC, item_order ASC, year ASC
            """.trimIndent()

            val blockMap = mutableMapOf<String, JusticeBlock>()
            val orderedBlocks = mutableListOf<String>()
            val allYears = mutableSetOf<Int>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val blockCode = rs.getString("block_code")
                        val blockName = rs.getString("block_name")
                        val itemCode = rs.getString("item_code")
                        val year = rs.getInt("year")

                        if (!blockMap.containsKey(blockCode)) {
                            orderedBlocks.add(blockCode)
                            blockMap[blockCode] = JusticeBlock(blockCode, blockName)
                        }

                        val block = blockMap[blockCode]!!
                        var item = block.items.find { it.itemCode == itemCode }

                        if (item == null) {
                            item = JusticeData(
                                itemCode = itemCode,
                                department = rs.getString("department") ?: "",
                                description = rs.getString("description") ?: ""
                            )
                            block.items.add(item)
                        }

                        allYears.add(year)
                        item.yearlyCounts[year] = rs.getString("count_value")
                    }
                }
            }

            val sortedYears = allYears.sorted()

            // 1. Construct Flat Headers
            val headerRow = mutableListOf<HeaderItem>()
            headerRow.add(HeaderItem(title = "កូដ"))
            headerRow.add(HeaderItem(title = "ការពិពណ៌នា / អគ្គនាយកដ្ឋាន")) // Description / Department
            headerRow.add(HeaderItem(title = "ប្រភេទ")) // Sub-category for Block 3

            // Dynamically add Year columns to the header
            for (year in sortedYears) {
                headerRow.add(HeaderItem(title = year.toString()))
            }

            // 2. Construct Pivot Data Rows
            val tbRows = mutableListOf<List<Any?>>()

            for (blockCode in orderedBlocks) {
                val block = blockMap[blockCode]!!

                // Inject visual Block Separator Row
                val groupRow = mutableListOf<Any?>(block.blockCode, block.blockName, "")
                for (y in sortedYears) groupRow.add("") // Pad the year columns for the grouping row
                tbRows.add(groupRow)

                var lastDept = ""
                for (item in block.items) {
                    val row = mutableListOf<Any?>()
                    row.add(item.itemCode)

                    if (blockCode == "26A3_3") {
                        // Logic specifically for Block 3 where there is a middle Department level
                        if (item.department.isNotBlank() && item.department != lastDept) {
                            row.add(item.department)
                            lastDept = item.department
                        } else {
                            row.add("") // Keep it visually clean beneath the grouping
                        }
                        row.add(item.description)
                    } else {
                        // Block 1 and 2
                        row.add(item.description)
                        row.add("")
                    }

                    // Append each year's data dynamically
                    for (y in sortedYears) {
                        row.add(item.yearlyCounts[y])
                    }

                    tbRows.add(row)
                }
            }

            return DynamicReportResponse(
                title = "ក្រសួងយុត្តិធម៌", // Ministry of Justice
                sub_title = "26A3 ស្ថិតិយុត្តិធម៌ និងច្បាប់", // Justice and Legal Statistics
                tb_headers = listOf(headerRow),
                tb_rows = tbRows
            )
        }
    }
}