package com.kh.niswebportal.app.nis26.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

data class JusticeRecordData(
    val itemCode: String,
    val description: String,
    val yearlyCounts: MutableMap<Int, String?> = mutableMapOf()
)

data class JusticeBlockGroup(
    val blockCode: String,
    val blockName: String,
    val items: MutableList<JusticeRecordData> = mutableListOf()
)

@Service
class Nis26A6InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_26a6"

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
                    description,
                    year,
                    count_value
                FROM nis_26a6_justice_records
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY block_order ASC, item_order ASC, year ASC
            """.trimIndent()

            val blockMap = mutableMapOf<String, JusticeBlockGroup>()
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
                            blockMap[blockCode] = JusticeBlockGroup(blockCode, blockName)
                        }

                        val block = blockMap[blockCode]!!
                        var item = block.items.find { it.itemCode == itemCode }

                        if (item == null) {
                            item = JusticeRecordData(
                                itemCode = itemCode,
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
            headerRow.add(HeaderItem(title = "ការពិពណ៌នា"))

            // Dynamically add Year columns
            for (year in sortedYears) {
                headerRow.add(HeaderItem(title = year.toString()))
            }

            // 2. Construct Pivot Data Rows
            val tbRows = mutableListOf<List<Any?>>()

            for (blockCode in orderedBlocks) {
                val block = blockMap[blockCode]!!

                // A. Inject a visual Block Grouping Row
                val groupRow = mutableListOf<Any?>(block.blockCode, block.blockName)
                for (y in sortedYears) groupRow.add("") // Pad the year columns cleanly
                tbRows.add(groupRow)

                // B. Inject Item Data Rows
                for (item in block.items) {
                    val row = mutableListOf<Any?>()
                    row.add(item.itemCode)
                    row.add(item.description)

                    // Append each year's data dynamically
                    for (y in sortedYears) {
                        row.add(item.yearlyCounts[y])
                    }

                    tbRows.add(row)
                }
            }

            return DynamicReportResponse(
                title = "ក្រសួងយុត្តិធម៌", // Ministry of Justice
                sub_title = "26A6 ស្ថិតិការទទួលពាក្យសុំ និងដោះស្រាយវិវាទ", // App Receipt & Dispute Resolution Stats
                tb_headers = listOf(headerRow),
                tb_rows = tbRows
            )
        }
    }
}