package com.kh.niswebportal.app.nis16.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis16d1LiteracyInquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_16d1_lit"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
            val query = """
                SELECT budget_year, class_count, teacher_total, teacher_female, 
                       enrollment_total, enrollment_female, graduation_total
                FROM nis_16d1_literacy
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY budget_year ASC
            """.trimIndent()

            val tbRows = mutableListOf<List<Any?>>()
            var idx = 1

            conn.prepareStatement(query).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val row = mutableListOf<Any?>(
                            idx++,
                            rs.getString("budget_year") ?: "-",
                            rs.getString("class_count") ?: "-",
                            rs.getString("teacher_total") ?: "-",
                            rs.getString("teacher_female") ?: "-",
                            rs.getString("enrollment_total") ?: "-",
                            rs.getString("enrollment_female") ?: "-",
                            rs.getString("graduation_total") ?: "-"
                        )
                        tbRows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem(title = "ល.រ"),
                HeaderItem(title = "ឆ្នាំអនុវត្ត"),
                HeaderItem(title = "ថ្នាក់"),
                HeaderItem(title = "គ្រូ - សរុប"),
                HeaderItem(title = "គ្រូ - ស្រី"),
                HeaderItem(title = "ចូលរៀន - សរុប"),
                HeaderItem(title = "ចូលរៀន - ស្រី"),
                HeaderItem(title = "បញ្ចប់ - សរុប")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល អប់រំ យុវជន និងកីឡា",
                sub_title = "ចំនួនថ្នាក់រៀន គ្រូ សិស្សក្នុងកម្មវិធីអក្ខរកម្ម",
                tb_headers = listOf(topHeader),
                tb_rows = tbRows
            )
        }
    }
}