package com.kh.niswebportal.app.nis61.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis61t22InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_61t22"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT category_group, transition_status, sample_size, transited, in_transition, not_yet_started
                FROM nis_61t22_swts_stats
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
                        val status = rs.getString("transition_status") ?: ""

                        rows.add(
                            listOf(
                                idx++,
                                "[$group] $status",
                                rs.getString("sample_size") ?: "-",
                                rs.getString("transited") ?: "-",
                                rs.getString("in_transition") ?: "-",
                                rs.getString("not_yet_started") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ស្ថានភាពអន្តរកាល (Transition Status)"),
                HeaderItem("ទំហំសំណាកសរុប (Sample Size N)"),
                HeaderItem("បញ្ចប់អន្តរកាល (Transited)"),
                HeaderItem("ស្ថិតក្នុងអន្តរកាល (In Transition)"),
                HeaderItem("មិនទាន់ចាប់ផ្តើម (Not Yet Started)")
            )

            return DynamicReportResponse(
                title = "ស្ថិតិអង្កេតអន្តរកាលពីការសិក្សាទៅកាន់ទីផ្សារការងារប្រចាំឆ្នាំ (Annual School-to-Work Transition Survey - SWTS)",
                sub_title = "NIS 61T_22 - ស្ថិតិទីផ្សារការងារនិងកម្លាំងពលកម្ម",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}