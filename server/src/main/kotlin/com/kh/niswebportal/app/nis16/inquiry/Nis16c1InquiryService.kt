package com.kh.niswebportal.app.nis16.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis16c1InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_16c1"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
            val query = """
                SELECT nis_code, budget_year, chapter, account, sub_account, description,
                       financial_law, credit_inc_internal, credit_inc_unplan, credit_inc_add,
                       credit_inc_total, credit_dec, new_credit, actual_implementation,
                       credit_balance, percentage, pay_balance_expense
                FROM nis_16c1_budget_statistics
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id ASC
            """.trimIndent()

            val tbRows = mutableListOf<List<Any?>>()
            var idx = 1

            conn.prepareStatement(query).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val row = mutableListOf<Any?>(
                            idx++,                                    // 1. ល.រ
                            rs.getString("budget_year") ?: "-",       // 2. ឆ្នាំថវិកា (បង្ហាញបញ្ឈរ 2024, 2025...)
                            rs.getString("chapter") ?: "-",           // 3. ជំពូក
                            rs.getString("account") ?: "-",           // 4. គណនី
                            rs.getString("sub_account") ?: "-",       // 5. អនុគណនី
                            rs.getString("description") ?: "",        // 6. បរិយាយ
                            rs.getString("financial_law") ?: "-",
                            rs.getString("credit_inc_internal") ?: "-",
                            rs.getString("credit_inc_unplan") ?: "-",
                            rs.getString("credit_inc_add") ?: "-",
                            rs.getString("credit_inc_total") ?: "-",
                            rs.getString("credit_dec") ?: "-",
                            rs.getString("new_credit") ?: "-",
                            rs.getString("actual_implementation") ?: "-",
                            rs.getString("credit_balance") ?: "-",
                            rs.getString("percentage") ?: "-",
                            rs.getString("pay_balance_expense") ?: "-"
                        )
                        tbRows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem(title = "ល.រ"),
                HeaderItem(title = "ឆ្នាំថវិកា"),
                HeaderItem(title = "ជំពូក"),
                HeaderItem(title = "គណនី"),
                HeaderItem(title = "អនុគណនី"),
                HeaderItem(title = "បរិយាយ"),
                HeaderItem(title = "ច្បាប់ហិរញ្ញវត្ថុ"),
                HeaderItem(title = "ចលនាផ្ទៃក្នុង"),
                HeaderItem(title = "ឥណទានមិនបានគ្រោងទុក"),
                HeaderItem(title = "បំពេញបន្ថែម"),
                HeaderItem(title = "សរុបចលនាកើន"),
                HeaderItem(title = "ថយ"),
                HeaderItem(title = "ស្ថានភាពឥណទានថ្មី"),
                HeaderItem(title = "អនុវត្តជាក់ស្ដែង"),
                HeaderItem(title = "ឥណទាននៅសល់"),
                HeaderItem(title = "សមាមាត្រ %"),
                HeaderItem(title = "បង់សំណេសចំណាយ")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល អប់រំ យុវជន និងកីឡា",
                sub_title = "តារាងទី២០. របាយការណ៍ចំណាយថវិកាជាតិ សម្រាប់ការគ្រប់គ្រងវិស័យអប់រំ",
                tb_headers = listOf(topHeader),
                tb_rows = tbRows
            )
        }
    }
}