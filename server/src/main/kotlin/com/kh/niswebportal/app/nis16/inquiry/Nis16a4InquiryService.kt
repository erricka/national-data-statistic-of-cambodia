package com.kh.niswebportal.app.nis16.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis16a4InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_16a4"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    MIN(pro_code) AS pro_code,
                    pro_name,
                    year, 
                    SUM(CAST(school_count AS INTEGER)) AS school_count,
                    MIN(ctid) AS item_order
                FROM nis_16a4
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                GROUP BY pro_name, year
                ORDER BY item_order ASC, year ASC
            """.trimIndent()

            val itemsMap = mutableMapOf<String, MutableMap<Int, String?>>()
            val itemCodes = mutableMapOf<String, String?>()
            val orderedProNames = mutableListOf<String>()
            val yearsSet = mutableSetOf<Int>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val proName = rs.getString("pro_name")
                        val proCode = rs.getString("pro_code")
                        val year = rs.getInt("year")
                        val count = rs.getString("school_count")

                        if (!itemsMap.containsKey(proName)) {
                            orderedProNames.add(proName)
                            itemsMap[proName] = mutableMapOf()
                            itemCodes[proName] = proCode
                        }

                        itemsMap[proName]!![year] = count
                        yearsSet.add(year)
                    }
                }
            }

            val sortedYears = yearsSet.sorted()

            // 1. Construct 2-Tier Headers (Fixed duplication)
            val topHeader = mutableListOf<HeaderItem>()
            val subHeader = mutableListOf<HeaderItem>()

            // First two columns (ល.រ and រាជធានី ខេត្ត)
            topHeader.add(HeaderItem(title = "\u200B"))
            topHeader.add(HeaderItem(title = "\u200B"))

            subHeader.add(HeaderItem(title = "ល.រ"))
            subHeader.add(HeaderItem(title = "រាជធានី ខេត្ត"))

            // For each year: top header is left empty, sub-header gets the year
            sortedYears.forEach { year ->
                topHeader.add(HeaderItem(title = "\u200B"))
                subHeader.add(HeaderItem(title = year.toString()))
            }

            // 2. Construct the pivoted matrix rows
            val tbRows = mutableListOf<List<Any?>>()
            orderedProNames.forEachIndexed { idx, proName ->
                val row = mutableListOf<Any?>()
                row.add(idx + 1)             // ល.រ (Sequential Index)
                row.add(proName)             // រាជធានី ខេត្ត

                val itemData = itemsMap[proName]!!
                for (year in sortedYears) {
                    row.add(itemData[year] ?: "-")
                }
                tbRows.add(row)
            }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល អប់រំ យុវជន និងកីឡា",
                sub_title = "តារាងទី ៤. ចំនួនសាលារៀនមត្តេយ្យសិក្សា តាមរាជធានី ខេត្ត និងឆ្នាំសិក្សា",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = tbRows
            )
        }
    }
}