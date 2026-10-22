package com.kh.niswebportal.app.nis26.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

data class CaseStatsData(
    val serialNo: String,
    val description: String,
    val receivedCases: Double?,
    val solvedCases: Double?,
    val resolutionRate: Double?
)

data class CaseBlockGroup(
    val blockCode: String,
    val blockName: String,
    val items: MutableMap<String, MutableMap<Int, CaseStatsData>> = mutableMapOf() // Map<Description, Map<Year, Data>>
)

@Service
class Nis26A8InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_26a8"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY block_code) as block_order,
                    MIN(id) OVER (PARTITION BY description) as desc_order,
                    block_code,
                    block_name,
                    serial_no,
                    description,
                    year,
                    received_cases,
                    solved_cases,
                    resolution_rate
                FROM nis_26a8_campaign_cases
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY block_order ASC, desc_order ASC, year ASC
            """.trimIndent()

            val blockMap = mutableMapOf<String, CaseBlockGroup>()
            val orderedBlocks = mutableListOf<String>()
            val orderedDescriptions = mutableMapOf<String, MutableList<String>>() // Block -> List of Descriptions
            val allYears = mutableSetOf<Int>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val blockCode = rs.getString("block_code")
                        val blockName = rs.getString("block_name")
                        val description = rs.getString("description")
                        val year = rs.getInt("year")

                        if (!blockMap.containsKey(blockCode)) {
                            orderedBlocks.add(blockCode)
                            orderedDescriptions[blockCode] = mutableListOf()
                            blockMap[blockCode] = CaseBlockGroup(blockCode, blockName)
                        }

                        val block = blockMap[blockCode]!!

                        if (!block.items.containsKey(description)) {
                            orderedDescriptions[blockCode]!!.add(description)
                            block.items[description] = mutableMapOf()
                        }

                        allYears.add(year)

                        block.items[description]!![year] = CaseStatsData(
                            serialNo = rs.getString("serial_no") ?: "",
                            description = description,
                            receivedCases = rs.getNullableDouble("received_cases"),
                            solvedCases = rs.getNullableDouble("solved_cases"),
                            resolutionRate = rs.getNullableDouble("resolution_rate")
                        )
                    }
                }
            }

            val sortedYears = allYears.sorted()

            // 1. Construct 2-Tier Headers
            val topHeader = mutableListOf<HeaderItem>()
            val subHeader = mutableListOf<HeaderItem>()

            // Static Top Headers (Using \u200B trick for visual spacing)
            topHeader.add(HeaderItem(title = "\u200B")) // Serial
            topHeader.add(HeaderItem(title = "\u200B")) // Description

            // Static Sub Headers
            subHeader.add(HeaderItem(title = "លរ"))
            subHeader.add(HeaderItem(title = "បរិយាយ"))

            // Dynamically add Year columns
            for (year in sortedYears) {
                topHeader.add(HeaderItem(title = "ឆ្នាំ$year"))
                topHeader.add(HeaderItem(title = "")) // Span
                topHeader.add(HeaderItem(title = "")) // Span

                subHeader.add(HeaderItem(title = "សំណុំរឿងទទួលបាន"))
                subHeader.add(HeaderItem(title = "ដោះស្រាយរួច"))
                subHeader.add(HeaderItem(title = "គិតជា %"))
            }

            // 2. Construct Pivot Data Rows
            val tbRows = mutableListOf<List<Any?>>()

            for (blockCode in orderedBlocks) {
                val block = blockMap[blockCode]!!

                // A. Inject a visual Block Grouping Row
                val groupRow = mutableListOf<Any?>(block.blockCode, block.blockName)
                for (y in sortedYears) {
                    groupRow.add("") // Pad received
                    groupRow.add("") // Pad solved
                    groupRow.add("") // Pad rate
                }
                tbRows.add(groupRow)

                // B. Inject Item Data Rows
                for (desc in orderedDescriptions[blockCode]!!) {
                    val row = mutableListOf<Any?>()
                    val firstYearData = block.items[desc]!!.values.firstOrNull()

                    row.add(firstYearData?.serialNo ?: "")
                    row.add(desc)

                    // Append each year's data dynamically
                    for (y in sortedYears) {
                        val yearData = block.items[desc]?.get(y)
                        row.add(yearData?.receivedCases)
                        row.add(yearData?.solvedCases)
                        row.add(yearData?.resolutionRate)
                    }

                    tbRows.add(row)
                }
            }

            return DynamicReportResponse(
                title = "ក្រសួងយុត្តិធម៌", // Ministry of Justice
                sub_title = "26A8 ស្ថិតិរឿងក្តីត្រូវបានត្រួតពិនិត្យ ជុំរុញ និងដោះស្រាយភាពមិនប្រក្រតី", // Campaign Case Resolution Stats
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = tbRows
            )
        }
    }

    
}