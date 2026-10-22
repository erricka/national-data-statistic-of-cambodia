package com.kh.niswebportal.app.nis20.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis20A4InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_20a4"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY province_name) as prov_order,
                    province_name,
                    year,
                    community_count
                FROM nis_20a4_indigenous_communities
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY prov_order ASC, year ASC
            """.trimIndent()

            val provMap = mutableMapOf<String, MutableMap<Int, Int?>>()
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

                        provMap[prov]!![year] = rs.getNullableInt("community_count")
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
            topHeader.add(HeaderItem(title = "ឆ្នាំអនុវត្ត")) // Total Column

            // Static Sub Headers
            subHeader.add(HeaderItem(title = "ល.រ"))
            subHeader.add(HeaderItem(title = "ខេត្ត ក្រុង"))
            subHeader.add(HeaderItem(title = "ចំនួនសហគមន៍ជនជាតិដើមភាគតិចដែលបានទទួលស្គាល់អត្តសញ្ញាណ (សរុប)"))

            // Dynamic Yearly Headers
            for (year in sortedYears) {
                topHeader.add(HeaderItem(title = "ឆ្នាំ$year")) // Span above the year
                subHeader.add(HeaderItem(title = "ចំនួនសហគមន៍ (ចំនួន)"))
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
                row.add(overallTotal)

                // Append each year dynamically
                for (year in sortedYears) {
                    val yearData = provData[year]
                    row.add(yearData)
                }

                tbRows.add(row)
            }

            return DynamicReportResponse(
                title = "ក្រសួងអភិវឌ្ឍន៍ជនបទ", // Ministry of Rural Development
                sub_title = "20A4 ស្ថិតិក្រុមជនជាតិភាគតិច", // Indigenous Ethnic Groups Statistics
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