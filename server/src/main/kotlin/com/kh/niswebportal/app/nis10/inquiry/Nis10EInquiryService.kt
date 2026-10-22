package com.kh.niswebportal.app.nis10.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis10EInquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_10e"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    chapter,
                    account,
                    sub_account,
                    description_kh,
                    initial_finance_law,
                    internal_credit_movement,
                    unplanned_credit,
                    supplementary_credit,
                    total_increase_credit,
                    decrease_credit,
                    new_credit_status,
                    actual_execution,
                    remaining_credit,
                    ratio,
                    other_notes
                FROM nis_10e_annual_national_budget_expenditure
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
                        row.add(rs.getString("initial_finance_law") ?: "")
                        row.add(rs.getString("internal_credit_movement") ?: "")
                        row.add(rs.getString("unplanned_credit") ?: "")
                        row.add(rs.getString("supplementary_credit") ?: "")
                        row.add(rs.getString("total_increase_credit") ?: "")
                        row.add(rs.getString("decrease_credit") ?: "")
                        row.add(rs.getString("new_credit_status") ?: "")
                        row.add(rs.getString("actual_execution") ?: "")
                        row.add(rs.getString("remaining_credit") ?: "")
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
                HeaderItem("ច្បាប់ហិរញ្ញវត្ថុដើមឆ្នាំ"),
                HeaderItem("ចលនាផ្ទៃក្នុង"),
                HeaderItem("ឥណទានមិនបានគ្រោងទុក"),
                HeaderItem("បំពេញបន្ថែម"),
                HeaderItem("សរុបកើន"),
                HeaderItem("ថយ"),
                HeaderItem("ស្ថានភាពឥណទានថ្មី"),
                HeaderItem("អនុវត្តជាក់ស្តែង"),
                HeaderItem("ឥណទាននៅសល់"),
                HeaderItem("សមាមាត្រ (%)"),
                HeaderItem("ផ្សេងៗ")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាលក្រសួងសេដ្ឋកិច្ច និង ហិរញ្ញវត្ថុ",
                sub_title = "10E ស្ថិតិការចំណាយថវិកាជាតិប្រចាំឆ្នាំ",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }
}