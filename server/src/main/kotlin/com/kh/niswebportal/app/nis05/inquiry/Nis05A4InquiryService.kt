package com.kh.niswebportal.app.nis05.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import com.kh.niswebportal.core.util.KhmerStringUtils
import com.kh.niswebportal.core.util.getNullableDouble
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis05A4InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_05a4"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY item_name_khmer) as item_order,
                    item_code,
                    item_name_khmer,
                    unit,
                    year, 
                    month, 
                    retail_price
                FROM nis_05a4_retail_price
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY item_order ASC, year ASC, month ASC
            """.trimIndent()

            val itemsMap = mutableMapOf<String, MutableMap<Pair<Int, Int>, Double?>>()
            val itemCodes = mutableMapOf<String, String?>()
            val itemUnits = mutableMapOf<String, String?>()
            val orderedItemNames = mutableListOf<String>()
            val periods = mutableSetOf<Pair<Int, Int>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val itemName = rs.getString("item_name_khmer")
                        val itemCode = rs.getString("item_code")
                        val unit = rs.getString("unit")
                        val year = rs.getInt("year")
                        val month = rs.getInt("month")
                        val value = rs.getNullableDouble("retail_price")

                        if (!itemsMap.containsKey(itemName)) {
                            orderedItemNames.add(itemName)
                            itemsMap[itemName] = mutableMapOf()
                            itemCodes[itemName] = itemCode
                            itemUnits[itemName] = unit
                        }

                        val period = Pair(year, month)
                        itemsMap[itemName]!![period] = value
                        periods.add(period)
                    }
                }
            }

            val sortedPeriods = periods.toList().sortedWith(compareBy({ it.first }, { it.second }))
            val periodsByYear = sortedPeriods.groupBy { it.first }

            val topHeader = mutableListOf<HeaderItem>()
            val subHeader = mutableListOf<HeaderItem>()

            topHeader.add(HeaderItem(title = "\u200B"))
            topHeader.add(HeaderItem(title = "\u200B"))
            topHeader.add(HeaderItem(title = "\u200B"))

            subHeader.add(HeaderItem(title = "ល.រ"))
            subHeader.add(HeaderItem(title = "មុខទំនិញ"))
            subHeader.add(HeaderItem(title = "ឯកតា"))

            for ((year, yearPeriods) in periodsByYear) {
                yearPeriods.forEachIndexed { index, period ->
                    val yearTitle = if (index == 0) year.toString() else ""
                    topHeader.add(HeaderItem(title = yearTitle))
                    subHeader.add(HeaderItem(title = KhmerStringUtils.getKhmerMonthName(period.second)))
                }
            }

            val tbRows = mutableListOf<List<Any?>>()
            for (itemName in orderedItemNames) {
                val row = mutableListOf<Any?>()
                row.add(itemCodes[itemName])
                row.add(itemName)
                row.add(itemUnits[itemName])

                val itemData = itemsMap[itemName]!!
                for (period in sortedPeriods) {
                    row.add(itemData[period])
                }
                tbRows.add(row)
            }

            return DynamicReportResponse(
                title = "វិទ្យាស្ថានជាតិស្ថិតិ ក្រសួងផែនការ",
                sub_title = "05A4 ថ្លៃលក់រាយទំនិញសំខាន់ៗនៅរាជធានីភ្នំពេញ",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = tbRows
            )
        }
    }
}
