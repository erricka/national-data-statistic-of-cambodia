package com.kh.niswebportal.app.nis09.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

data class MissionData(
    val countryName: String,
    val numMissions: Int?
)

@Service
class Nis09A1InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_09a1"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY item_code) as item_order,
                    item_code,
                    country_name,
                    year,
                    num_missions
                FROM nis_09a1_missions_abroad
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY item_order ASC, year ASC
            """.trimIndent()

            val itemsMap = mutableMapOf<String, MutableMap<Int, MissionData>>()
            val orderedItemCodes = mutableListOf<String>()
            val allYears = mutableSetOf<Int>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val itemCode = rs.getString("item_code")
                        val year = rs.getInt("year")
                        val country = rs.getString("country_name")
                        val count = rs.getNullableInt("num_missions")

                        if (!itemsMap.containsKey(itemCode)) {
                            orderedItemCodes.add(itemCode)
                            itemsMap[itemCode] = mutableMapOf()
                        }

                        itemsMap[itemCode]!![year] = MissionData(country, count)
                        allYears.add(year)
                    }
                }
            }

            val sortedYears = allYears.sorted()

            // 1. Construct 2-Tier Headers
            val topHeader = mutableListOf<HeaderItem>()
            val subHeader = mutableListOf<HeaderItem>()

            // First Column for ID (No.)
            topHeader.add(HeaderItem(title = "\u200B"))
            subHeader.add(HeaderItem(title = "ល.រ"))

            for (year in sortedYears) {
                // Tier 1: Year text followed by an invisible space to mimic 'colSpan=2' for the UI
                topHeader.add(HeaderItem(title = year.toString()))
                topHeader.add(HeaderItem(title = "\u200B"))

                // Tier 2: The repeating sub-columns identical to the Excel format
                subHeader.add(HeaderItem(title = "ប្រទេស"))
                subHeader.add(HeaderItem(title = "ចំនួនស្ថានតំណាង"))
            }

            // 2. Construct Data Rows
            val tbRows = mutableListOf<List<Any?>>()
            for (itemCode in orderedItemCodes) {
                val row = mutableListOf<Any?>()

                // Format missing IDs cleanly (like Timor-Leste's generated code) back into an empty string for UI
                val displayCode = if (itemCode.startsWith("09A1_R")) "" else itemCode
                row.add(displayCode)

                val itemData = itemsMap[itemCode]!!
                for (year in sortedYears) {
                    val data = itemData[year]
                    if (data != null) {
                        row.add(data.countryName)
                        row.add(data.numMissions)
                    } else {
                        // If the mission didn't exist in that specific year (e.g. Bulgaria in 2016)
                        row.add("")
                        row.add("")
                    }
                }
                tbRows.add(row)
            }

            return DynamicReportResponse(
                title = "ក្រសួងការបរទេស និងសហប្រតិបត្តិការអន្តរជាតិ", // Ministry of Foreign Affairs
                sub_title = "09A1 ស្ថិតិស្ថានតំណាងកម្ពុជាប្រចាំនៅបរទេស ប្រចាំឆ្នាំ", // Cambodian Missions Abroad
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