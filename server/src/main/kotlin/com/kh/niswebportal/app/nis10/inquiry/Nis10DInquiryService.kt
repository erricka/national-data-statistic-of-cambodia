package com.kh.niswebportal.app.nis10.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis10DInquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_10d"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    chapter,
                    account,
                    sub_account,
                    description_kh,
                    finance_law_amount,
                    actual_execution,
                    ratio,
                    other_notes
                FROM nis_10d_annual_national_budget_revenue
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
                        row.add(rs.getString("chapter") ?: "")
                        row.add(rs.getString("account") ?: "")
                        row.add(rs.getString("sub_account") ?: "")
                        row.add(rs.getString("description_kh") ?: "")
                        row.add(rs.getString("finance_law_amount") ?: "")
                        row.add(rs.getString("actual_execution") ?: "")
                        row.add(rs.getString("ratio") ?: "")
                        row.add(rs.getString("other_notes") ?: "")

                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ជំពូក"),
                HeaderItem("គណនី"),
                HeaderItem("អនុគណនី"),
                HeaderItem("បរិយាយ"),
                HeaderItem("ច្បាប់ហិរញ្ញវត្ថុ"),
                HeaderItem("អនុវត្តជាក់ស្តែង"),
                HeaderItem("សមាមាត្រ"),
                HeaderItem("ផ្សេងៗ")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាលក្រសួងសេដ្ឋកិច្ច និង ហិរញ្ញវត្ថុ",
                sub_title = "10D ស្ថិតិចំណូលថវិកាជាតិប្រចាំឆ្នាំ",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }
}