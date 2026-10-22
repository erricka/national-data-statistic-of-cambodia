package com.kh.niswebportal.app.nis20.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

data class VillageData(
    val committeesCount: Int?,
    val mixedVillagesCount: Int?,
    val modelVillagesCount: Int?
)

@Service
class Nis20A5InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_20a5"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY province_name) as prov_order,
                    province_name,
                    year,
                    committees_count,
                    mixed_villages_count,
                    model_villages_count
                FROM nis_20a5_model_villages
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY prov_order ASC, year ASC
            """.trimIndent()

            val provMap = mutableMapOf<String, MutableMap<Int, VillageData>>()
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

                        provMap[prov]!![year] = VillageData(
                            committeesCount = rs.getNullableInt("committees_count"),
                            mixedVillagesCount = rs.getNullableInt("mixed_villages_count"),
                            modelVillagesCount = rs.getNullableInt("model_villages_count")
                        )
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
            topHeader.add(HeaderItem(title = "ឆ្នាំអនុវត្ត")) // Total: Committees
            topHeader.add(HeaderItem(title = ""))       // Total: Mixed Villages
            topHeader.add(HeaderItem(title = ""))       // Total: Model Villages

            // Static Sub Headers
            subHeader.add(HeaderItem(title = "ល.រ"))
            subHeader.add(HeaderItem(title = "ខេត្ត ក្រុង"))
            subHeader.add(HeaderItem(title = "ចំនួនគណៈកម្មការគ.អ.ភ"))
            subHeader.add(HeaderItem(title = "ភូមិចម្រុះ"))
            subHeader.add(HeaderItem(title = "ភូមិគំរូ"))

            // Dynamic Yearly Headers
            for (year in sortedYears) {
                topHeader.add(HeaderItem(title = "ឆ្នាំ$year")) // Span above the year
                subHeader.add(HeaderItem(title = "ភូមិគំរូ"))
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
                row.add(overallTotal?.committeesCount)
                row.add(overallTotal?.mixedVillagesCount)
                row.add(overallTotal?.modelVillagesCount)

                // Append each year dynamically
                for (year in sortedYears) {
                    val yearData = provData[year]
                    row.add(yearData?.modelVillagesCount)
                }

                tbRows.add(row)
            }

            return DynamicReportResponse(
                title = "ក្រសួងអភិវឌ្ឍន៍ជនបទ", // Ministry of Rural Development
                sub_title = "20A5 ស្ថិតិប្រកាសទទួលស្គាល់ភូមិគំរូប្រចាំឆ្នាំ", // Statistics of Model Village Recognition
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