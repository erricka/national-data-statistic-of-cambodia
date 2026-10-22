package com.kh.niswebportal.app.nis24.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis24A3InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_24a3"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    serial_no,
                    case_date,
                    violent_type,
                    place_case,
                    service_fb,
                    service_telegram,
                    service_direct,
                    victim_age,
                    victim_hh_status,
                    intervention_service,
                    intervention_dispatch,
                    intervention_other
                FROM nis_24a3_gbv_services
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id ASC
            """.trimIndent()

            // 1. Construct the exact 2-Tier Headers to match the Excel format
            val topHeader = listOf(
                HeaderItem(title = "ល.រ"),
                HeaderItem(title = "កាលបរិច្ឆេទ"),
                HeaderItem(title = "ប្រភេទហិង្សា"),
                HeaderItem(title = "ទីតាំងកើតហេតុ"),
                HeaderItem(title = "ទទួលសេវា"),
                HeaderItem(title = ""), // Span
                HeaderItem(title = ""), // Span
                HeaderItem(title = "អាយុជនរងគ្រោះ"),
                HeaderItem(title = "ស្ថានភាពគ្រួសារ"),
                HeaderItem(title = "ចំណាត់ការ"),
                HeaderItem(title = ""), // Span
                HeaderItem(title = "")  // Span
            )

            val subHeader = listOf(
                HeaderItem(title = "\u200B"),
                HeaderItem(title = "\u200B"),
                HeaderItem(title = "\u200B"),
                HeaderItem(title = "\u200B"),
                HeaderItem(title = "Page FB MoWA"),
                HeaderItem(title = "Helpline/Telegram"),
                HeaderItem(title = "ផ្ទាល់"),
                HeaderItem(title = "\u200B"),
                HeaderItem(title = "\u200B"),
                HeaderItem(title = "ផ្តល់សេវា"),
                HeaderItem(title = "បញ្ជូនបន្ត"),
                HeaderItem(title = "ផ្សេងៗ")
            )

            val tbRows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        tbRows.add(
                            listOf(
                                rs.getString("serial_no") ?: "",
                                rs.getString("case_date") ?: "",
                                rs.getString("violent_type") ?: "",
                                rs.getString("place_case") ?: "",
                                rs.getString("service_fb") ?: "",
                                rs.getString("service_telegram") ?: "",
                                rs.getString("service_direct") ?: "",
                                rs.getString("victim_age") ?: "",
                                rs.getString("victim_hh_status") ?: "",
                                rs.getString("intervention_service") ?: "",
                                rs.getString("intervention_dispatch") ?: "",
                                rs.getString("intervention_other") ?: ""
                            )
                        )
                    }
                }
            }

            return DynamicReportResponse(
                title = "ស្ថិតិយេនឌ័រ", // Gender Statistics
                sub_title = "24A3 ស្ថិតិប្រភេទសេវាដែលបានផ្តល់ជូនជនរងគ្រោះដោយអំពើហិង្សាទាក់ទងនឹងយេនឌ័រ", // GBV Victim Services Log
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = tbRows
            )
        }
    }
}