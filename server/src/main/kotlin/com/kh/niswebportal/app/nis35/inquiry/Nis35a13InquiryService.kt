package com.kh.niswebportal.app.nis35.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis35a13InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_35a13"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    year,
                    month,
                    unit,
                    market_fx_purchase,
                    market_fx_sale,
                    market_fx_midpoint,
                    official_fx_purchase,
                    official_fx_sale,
                    official_fx_midpoint,
                    market_buying_rate_m2m,
                    market_buying_rate_last_year
                FROM nis_35a13_monthly_average_exchange_rate
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()
                        row.add(index++)
                        row.add(rs.getString("year") ?: "-")
                        row.add(rs.getString("month") ?: "-")
                        row.add(rs.getString("unit") ?: "Riel/U.S $")
                        row.add(rs.getString("market_fx_purchase") ?: "-")
                        row.add(rs.getString("market_fx_sale") ?: "-")
                        row.add(rs.getString("market_fx_midpoint") ?: "-")
                        row.add(rs.getString("official_fx_purchase") ?: "-")
                        row.add(rs.getString("official_fx_sale") ?: "-")
                        row.add(rs.getString("official_fx_midpoint") ?: "-")
                        row.add(rs.getString("market_buying_rate_m2m") ?: "-")
                        row.add(rs.getString("market_buying_rate_last_year") ?: "-")
                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("Year"),
                HeaderItem("Month"),
                HeaderItem("Unit"),
                HeaderItem("Market Exchange Rate - Purchase"),
                HeaderItem("Market Exchange Rate - Sale"),
                HeaderItem("Market Exchange Rate - Midpoint"),
                HeaderItem("Official Exchange Rate - Purchase"),
                HeaderItem("Official Exchange Rate - Sale"),
                HeaderItem("Official Exchange Rate - Midpoint"),
                HeaderItem("Percent change of the market buying rate - M. to M."),
                HeaderItem("Percent change of the market buying rate - From the End of last Year Through Indicated Month")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ធនាគារជាតិនៃកម្ពុជា",
                sub_title = "35A13 Monthly Average Exchange Rate",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }
}