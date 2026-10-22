package com.kh.niswebportal.app.nis35.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.util.LinkedHashMap
import java.util.HashMap
import java.util.HashSet
import java.util.ArrayList
import javax.sql.DataSource

data class PeriodKey(
    val year: Int,
    val quarter: Int
) : Comparable<PeriodKey> {
    override fun compareTo(other: PeriodKey): Int {
        val yearCmp = this.year.compareTo(other.year)
        return if (yearCmp != 0) yearCmp else this.quarter.compareTo(other.quarter)
    }
}

@Service
class Nis35A1InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_35a1"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY item_code) as item_order,
                    item_code,
                    item_name,
                    year, 
                    quarter, 
                    index_value
                FROM nis_35a1_balance_of_payments
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY item_order ASC, year ASC, quarter ASC
            """.trimIndent()

            val itemsMap = LinkedHashMap<String, MutableMap<PeriodKey, String?>>()
            val itemNames = HashMap<String, String>()
            val periods = HashSet<PeriodKey>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val itemCode = rs.getString("item_code")
                        val itemName = rs.getString("item_name")
                        val year = rs.getInt("year")
                        val quarter = rs.getInt("quarter")
                        val value = rs.getString("index_value")

                        val itemData = itemsMap.getOrPut(itemCode) { LinkedHashMap<PeriodKey, String?>() }
                        itemNames[itemCode] = itemName

                        val period = PeriodKey(year, quarter)
                        itemData[period] = value
                        periods.add(period)
                    }
                }
            }

            val sortedPeriods = periods.sorted()
            val periodsByYear = sortedPeriods.groupBy { it.year }

            val topHeader = ArrayList<HeaderItem>()
            val subHeader = ArrayList<HeaderItem>()

            topHeader.add(HeaderItem(title = "\u200B"))
            topHeader.add(HeaderItem(title = "\u200B"))

            subHeader.add(HeaderItem(title = "ល.រ"))
            subHeader.add(HeaderItem(title = "Main Components"))

            for ((year, yearPeriods) in periodsByYear) {
                yearPeriods.forEachIndexed { index, period ->
                    val yearTitle = if (index == 0) year.toString() else ""
                    topHeader.add(HeaderItem(title = yearTitle))

                    val quarterLabel = if (period.quarter == 0) year.toString() else "Q${period.quarter}"
                    subHeader.add(HeaderItem(title = quarterLabel))
                }
            }

            val tbRows = ArrayList<List<Any?>>()
            for ((itemCode, itemData) in itemsMap) {
                val row = ArrayList<Any?>()
                row.add(itemCode)
                row.add(itemNames[itemCode])

                for (period in sortedPeriods) {
                    row.add(itemData[period])
                }
                tbRows.add(row)
            }

            return DynamicReportResponse(
                title = "ធនាគារជាតិនៃកម្ពុជា",
                sub_title = "35A1 ស្ថិតិជញ្ជីងទូទាត់ (Balance of Payments)",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = tbRows
            )
        }
    }
}