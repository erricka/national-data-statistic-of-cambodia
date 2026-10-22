package com.kh.niswebportal.app.nis16.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis16c2InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_16c2"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
            val query = """
                SELECT nis_code, budget_year, chapter, account, sub_account, description,
                       financial_law, actual_implementation, percentage, notes
                FROM nis_16c2_budget_revenue
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
                            rs.getString("budget_year") ?: "-",       // 2. ឆ្នាំថវិកា (បង្ហាញបញ្ឈរ 2020, 2021, 2022...)
                            rs.getString("chapter") ?: "-",           // 3. ជំពូក
                            rs.getString("account") ?: "-",           // 4. គណនី
                            rs.getString("sub_account") ?: "-",       // 5. អនុគណនី
                            rs.getString("description") ?: "",        // 6. បរិយាយប្រភេទចំណូល
                            rs.getString("financial_law") ?: "-",     // 7. ច្បាប់ហិរញ្ញវត្ថុ
                            rs.getString("actual_implementation") ?: "-", // 8. អនុវត្តជាក់ស្តែង
                            rs.getString("percentage") ?: "-",        // 9. សមាមាត្រ %
                            rs.getString("notes") ?: "-"              // 10. បន្ថែម/កំណត់សម្គាល់
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
                HeaderItem(title = "បរិយាយប្រភេទចំណូល"),
                HeaderItem(title = "ច្បាប់ហិរញ្ញវត្ថុ"),
                HeaderItem(title = "អនុវត្តជាក់ស្តែង"),
                HeaderItem(title = "សមាមាត្រ %"),
                HeaderItem(title = "ចំណាំ")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល អប់រំ យុវជន និងកីឡា",
                sub_title = "តារាងទី២១. របាយការណ៍ចំណូលថវិកាថ្នាក់ជាតិក្នុងវិស័យអប់រំ",
                tb_headers = listOf(topHeader),
                tb_rows = tbRows
            )
        }
    }
}