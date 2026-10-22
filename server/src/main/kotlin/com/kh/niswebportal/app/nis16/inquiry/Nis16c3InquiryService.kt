package com.kh.niswebportal.app.nis16.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis16c3InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_16c3"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
            val query = """
                SELECT budget_year, category_name, indicator_value
                FROM nis_16c3_sports_statistics
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
                            rs.getString("budget_year") ?: "-",       // 2. ឆ្នាំថវិកា (បង្ហាញបញ្ឈរ)
                            rs.getString("category_name") ?: "",      // 3. ជំនាញសិក្សា / ប្រភេទសូចនាករ
                            rs.getString("indicator_value") ?: "-"    // 4. ចំនួន (តម្លៃ)
                        )
                        tbRows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem(title = "ល.រ"),
                HeaderItem(title = "ឆ្នាំថវិកា"),
                HeaderItem(title = "ជំនាញសិក្សា / សូចនាករ"),
                HeaderItem(title = "ចំនួន")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល អប់រំ យុវជន និងកីឡា",
                sub_title = "តារាងទី ២២. ចំនួនកីឡាករ អត្តពលិក បុគ្គលិក តាមប្រភេទកីឡា និងសហព័ន្ធកីឡា",
                tb_headers = listOf(topHeader),
                tb_rows = tbRows
            )
        }
    }
}