package com.kh.niswebportal.app.nis61.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis61t24InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_61t24"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT occupational_category, over_educated_mismatch, under_educated_mismatch,
                       over_skilling_mismatch, under_skilling_mismatch, field_of_study_mismatch,
                       foundation_skills_needed, hard_skills_needed, soft_skills_needed
                FROM nis_61t24_skills_mismatch_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(dataSql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var idx = 1
                    while (rs.next()) {
                        rows.add(
                            listOf(
                                idx++,
                                rs.getString("occupational_category") ?: "",
                                rs.getString("over_educated_mismatch") ?: "-",
                                rs.getString("under_educated_mismatch") ?: "-",
                                rs.getString("over_skilling_mismatch") ?: "-",
                                rs.getString("under_skilling_mismatch") ?: "-",
                                rs.getString("field_of_study_mismatch") ?: "-",
                                rs.getString("foundation_skills_needed") ?: "-",
                                rs.getString("hard_skills_needed") ?: "-",
                                rs.getString("soft_skills_needed") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ប្រភេទមុខរបរ (Occupational Category - ISCO 08)"),
                HeaderItem("អត្រាលើសគុណវុឌ្ឍិ (Over-educated Mismatch)"),
                HeaderItem("អត្រាខ្វះគុណវុឌ្ឍិ (Under-educated Mismatch)"),
                HeaderItem("អត្រាលើសជំនាញ (Over-skilling Mismatch)"),
                HeaderItem("អត្រាខ្វះជំនាញ (Under-skilling Mismatch)"),
                HeaderItem("អត្រាមុខជំនាញសិក្សា (Field-of-study Mismatch)"),
                HeaderItem("កម្រិតជំនាញមូលដ្ឋាន (Foundation Skills Needed)"),
                HeaderItem("ជំនាញបច្ចេកទេស (Hard Skills Needed)"),
                HeaderItem("ជំនាញទន់ (Soft Skills Needed)")
            )

            return DynamicReportResponse(
                title = "ស្ថិតិអង្កេតភាពមិនស៊ីគ្នានៃជំនាញនិងគុណវុឌ្ឍិក្នុងប្រទេសកម្ពុជាប្រចាំឆ្នាំ (Annual Skills and Qualifications Mismatch Statistics in Cambodia)",
                sub_title = "NIS 61T_24 - ស្ថិតិទីផ្សារការងារនិងកម្លាំងពលកម្ម",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}