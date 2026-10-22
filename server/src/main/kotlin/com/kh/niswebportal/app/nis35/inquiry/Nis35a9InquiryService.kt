package com.kh.niswebportal.app.nis35.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis35a9InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_35a9"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
// 1. Fetch dynamic list of distinct period/year values
            val years = mutableListOf<String>()
            val yearSql = """
                SELECT DISTINCT year 
                FROM nis_35a9_deposit_money_banks_interest_rates
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY year ASC
            """.trimIndent()

            conn.prepareStatement(yearSql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        years.add(rs.getString("year"))
                    }
                }
            }

            // 2. Aggregate pivoted dynamic period columns with escaped aliases
            val yearSelects = years.joinToString(",\n") { y ->
                "MAX(CASE WHEN year = '$y' THEN metric_value END) AS \"$y\""
            }

            val sql = """
                SELECT 
                    indicator_code,
                    indicator_en,
                    unit
                    ${if (years.isNotEmpty()) ", $yearSelects" else ""}
                FROM nis_35a9_deposit_money_banks_interest_rates
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                GROUP BY indicator_code, indicator_en, unit
                ORDER BY indicator_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(index++)
                        row.add(rs.getString("indicator_code"))
                        row.add(rs.getString("indicator_en"))
                        row.add(rs.getString("unit") ?: "Percentage")

                        for (y in years) {
                            row.add(rs.getString(y) ?: "-")
                        }

                        rows.add(row)
                    }
                }
            }

            // 3. Construct headers side-by-side
            val topHeader = mutableListOf(
                HeaderItem("ល.រ"),
                HeaderItem("Indicator Code"),
                HeaderItem("Deposit Money Bank's Interest Rates on Deposits and Loans"),
                HeaderItem("Unit")
            )
            years.forEach { y -> topHeader.add(HeaderItem(y)) }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ធនាគារជាតិនៃកម្ពុជា",
                sub_title = "35A9 Deposit Money Bank's Interest Rates on Deposits and Loans in KHR and USD",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }
}