package com.kh.niswebportal.app.nis29.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis29b1RainInquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_29b1_rain"

    // Set this to true if you want English months in the report headers, or false for Khmer
    private val useEnglishMonths: Boolean = false

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
// 1. Fetch data with chronological sorting
            val sql = """
                SELECT 
                    MIN(ctid) OVER (PARTITION BY pro_name) as item_order,
                    pro_code,
                    pro_name,
                    year, 
                    month, 
                    rainfall
                FROM nis_29b1_rain
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY item_order ASC, year ASC, month ASC
            """.trimIndent()

            val itemsMap = mutableMapOf<String, MutableMap<Pair<Int, Int>, String?>>()
            val itemCodes = mutableMapOf<String, String?>()
            val orderedProNames = mutableListOf<String>()
            val periods = mutableSetOf<Pair<Int, Int>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val proName = rs.getString("pro_name")
                        val proCode = rs.getString("pro_code")
                        val year = rs.getInt("year")
                        val month = rs.getInt("month")
                        val rainfall = rs.getString("rainfall")

                        if (!itemsMap.containsKey(proName)) {
                            orderedProNames.add(proName)
                            itemsMap[proName] = mutableMapOf()
                            itemCodes[proName] = proCode
                        }

                        val period = Pair(year, month)
                        itemsMap[proName]!![period] = rainfall
                        periods.add(period)
                    }
                }
            }

            // 2. Sort periods chronologically
            val sortedPeriods = periods.toList().sortedWith(compareBy({ it.first }, { it.second }))
            val periodsByYear = sortedPeriods.groupBy { it.first }

            // 3. Construct 2-Tier Headers
            val topHeader = mutableListOf<HeaderItem>()
            val subHeader = mutableListOf<HeaderItem>()

            topHeader.add(HeaderItem(title = "\u200B"))
            topHeader.add(HeaderItem(title = "\u200B"))

            subHeader.add(HeaderItem(title = "ល.រ"))
            subHeader.add(HeaderItem(title = "ស្ថានីយ / ខេត្ត"))

            for ((year, yearPeriods) in periodsByYear) {
                yearPeriods.forEachIndexed { index, period ->
                    val yearTitle = if (index == 0) year.toString() else ""
                    topHeader.add(HeaderItem(title = yearTitle))

                    // Supports toggling between English and Khmer month headers
                    val monthTitle = if (useEnglishMonths) getEnglishMonth(period.second) else getKhmerMonth(period.second)
                    subHeader.add(HeaderItem(title = monthTitle))
                }
            }

            // 4. Construct the pivoted matrix rows
            val tbRows = mutableListOf<List<Any?>>()
            orderedProNames.forEachIndexed { idx, proName ->
                val row = mutableListOf<Any?>()
                row.add(idx + 1)             // ល.រ (Sequential Index)
                row.add(proName)             // ស្ថានីយ / ខេត្ត

                val itemData = itemsMap[proName]!!
                for (period in sortedPeriods) {
                    row.add(itemData[period] ?: "-")
                }
                tbRows.add(row)
            }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងធនធានទឹក និងឧតុនិយម",
                sub_title = "29B1 ស្ថិតិស្ថានភាពទឹកភ្លៀងប្រចាំខែ",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = tbRows
            )
        }
    }

    private fun getKhmerMonth(month: Int): String {
        return when (month) {
            1 -> "មករា"
            2 -> "កុម្ភៈ"
            3 -> "មីនា"
            4 -> "មេសា"
            5 -> "ឧសភា"
            6 -> "មិថុនា"
            7 -> "កក្កដា"
            8 -> "សីហា"
            9 -> "កញ្ញា"
            10 -> "តុលា"
            11 -> "វិច្ឆិកា"
            12 -> "ធ្នូ"
            else -> ""
        }
    }

    private fun getEnglishMonth(month: Int): String {
        return when (month) {
            1 -> "Jan"
            2 -> "Feb"
            3 -> "Mar"
            4 -> "Apr"
            5 -> "May"
            6 -> "Jun"
            7 -> "Jul"
            8 -> "Aug"
            9 -> "Sep"
            10 -> "Oct"
            11 -> "Nov"
            12 -> "Dec"
            else -> ""
        }
    }
}