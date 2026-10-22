package com.kh.niswebportal.app.nis61.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis61t23InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_61t23"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT category_group, indicator_name, total_graduates, employed_or_self_employed,
                       further_education, looking_for_work, employment_rate, over_educated_mismatch,
                       under_educated_mismatch, over_skilling_mismatch, under_skilling_mismatch,
                       field_of_study_mismatch
                FROM nis_61t23_tvet_employment_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(dataSql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var idx = 1
                    while (rs.next()) {
                        val group = rs.getString("category_group") ?: ""
                        val indicator = rs.getString("indicator_name") ?: ""

                        rows.add(
                            listOf(
                                idx++,
                                "[$group] $indicator",
                                rs.getString("total_graduates") ?: "-",
                                rs.getString("employed_or_self_employed") ?: "-",
                                rs.getString("further_education") ?: "-",
                                rs.getString("looking_for_work") ?: "-",
                                rs.getString("employment_rate") ?: "-",
                                rs.getString("over_educated_mismatch") ?: "-",
                                rs.getString("under_educated_mismatch") ?: "-",
                                rs.getString("over_skilling_mismatch") ?: "-",
                                rs.getString("under_skilling_mismatch") ?: "-",
                                rs.getString("field_of_study_mismatch") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("គ្រឹះស្ថានបណ្តុះបណ្តាល / កម្រិតសញ្ញាបត្រ (Institution / Degree Level)"),
                HeaderItem("ចំនួនអ្នកបញ្ចប់ការសិក្សាសរុប (Total Graduates)"),
                HeaderItem("ទទួលបានការងារធ្វើ/ប្រកបមុខរបរផ្ទាល់ខ្លួន (Employed / Self-Employed)"),
                HeaderItem("បន្តការសិក្សា (Further Education)"),
                HeaderItem("កំពុងស្វែងរកការងារធ្វើ (Looking for Work)"),
                HeaderItem("អត្រាមានការងារធ្វើ (%) (Employment Rate)"),
                HeaderItem("អត្រាលើសគុណវុឌ្ឍិ (Over-educated Mismatch)"),
                HeaderItem("អត្រាខ្វះគុណវុឌ្ឍិ (Under-educated Mismatch)"),
                HeaderItem("អត្រាលើសជំនាញ (Over-skilling Mismatch)"),
                HeaderItem("អត្រាខ្វះជំនាញ (Under-skilling Mismatch)"),
                HeaderItem("អត្រាមុខជំនាញសិក្សា (Field-of-study Mismatch)")
            )

            return DynamicReportResponse(
                title = "ស្ថិតិអង្កេតស្ថានភាពការងាររបស់សិស្សបញ្ចប់การបណ្តុះបណ្តាលបច្ចេកទេស និងវិជ្ជាជីវៈ (TVET) ប្រចាំឆ្នាំ (Annual TVET Graduates Employment Status Survey Statistics)",
                sub_title = "NIS 61T_23 - ស្ថិតិទីផ្សារការងារនិងកម្លាំងពលកម្ម",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}