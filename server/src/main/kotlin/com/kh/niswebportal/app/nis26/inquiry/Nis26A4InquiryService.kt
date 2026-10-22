package com.kh.niswebportal.app.nis26.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

data class LawDisseminationData(
    val itemCode: String,
    val description: String,
    val unit: String,
    val yearlyCounts: MutableMap<Int, String?> = mutableMapOf()
)

@Service
class Nis26A4InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_26a4"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY description) as desc_order,
                    item_code,
                    description,
                    unit,
                    year,
                    count_value
                FROM nis_26a4_law_dissemination
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY desc_order ASC, year ASC
            """.trimIndent()

            val recordMap = mutableMapOf<String, LawDisseminationData>()
            val orderedDescriptions = mutableListOf<String>()
            val allYears = mutableSetOf<Int>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val description = rs.getString("description")
                        val year = rs.getInt("year")

                        if (!recordMap.containsKey(description)) {
                            orderedDescriptions.add(description)
                            recordMap[description] = LawDisseminationData(
                                itemCode = rs.getString("item_code") ?: "",
                                description = description,
                                unit = rs.getString("unit") ?: ""
                            )
                        }

                        allYears.add(year)
                        recordMap[description]!!.yearlyCounts[year] = rs.getString("count_value")
                    }
                }
            }

            val sortedYears = allYears.sorted()

            // 1. Construct Flat Headers
            val headerRow = mutableListOf<HeaderItem>()
            headerRow.add(HeaderItem(title = "\u200B")) // Empty space for Item Code column (matches Excel)
            headerRow.add(HeaderItem(title = "កិច្ចការផ្សព្វផ្សាយ")) // Task Description
            headerRow.add(HeaderItem(title = "ឯកតា")) // Unit

            // Dynamically add Year columns to the header
            for (year in sortedYears) {
                headerRow.add(HeaderItem(title = year.toString()))
            }

            // 2. Construct Pivot Data Rows
            val tbRows = mutableListOf<List<Any?>>()

            for (desc in orderedDescriptions) {
                val row = mutableListOf<Any?>()
                val data = recordMap[desc]!!

                row.add(data.itemCode)
                row.add(data.description)
                row.add(data.unit)

                // Append each year's data dynamically
                for (year in sortedYears) {
                    row.add(data.yearlyCounts[year])
                }

                tbRows.add(row)
            }

            return DynamicReportResponse(
                title = "ក្រសួងយុត្តិធម៌", // Ministry of Justice
                sub_title = "26A4 ស្ថិតិកិច្ចការផ្សព្វផ្សាយច្បាប់ប្រចាំឆ្នាំ", // Law Dissemination Stats
                tb_headers = listOf(headerRow),
                tb_rows = tbRows
            )
        }
    }
}