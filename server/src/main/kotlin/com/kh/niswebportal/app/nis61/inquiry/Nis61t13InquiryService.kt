package com.kh.niswebportal.app.nis61.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis61t13InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_61t13"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT social_security_scheme, member_2025, female_2025, member_2026,
                       female_2026, member_cumulative, female_cumulative
                FROM nis_61t13_nssf_membership
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
                                rs.getString("social_security_scheme") ?: "",
                                rs.getString("member_2025") ?: "-",
                                rs.getString("female_2025") ?: "-",
                                rs.getString("member_2026") ?: "-",
                                rs.getString("female_2026") ?: "-",
                                rs.getString("member_cumulative") ?: "-",
                                rs.getString("female_cumulative") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("របបសន្តិសុខសង្គម (Social Security Scheme)"),
                HeaderItem("សមាជិក ឆ្នាំ ២០២៥ (2025 Members)"),
                HeaderItem("ស្រី ឆ្នាំ ២០២៥ (2025 Female)"),
                HeaderItem("សមាជិក ឆ្នាំ ២០២៦ (2026 Members)"),
                HeaderItem("ស្រី ឆ្នាំ ២០២៦ (2026 Female)"),
                HeaderItem("សមាជិក សរុបสะสม (Cumulative Members)"),
                HeaderItem("ស្រី សរុបสะสม (Cumulative Female)")
            )

            return DynamicReportResponse(
                title = "ស្ថិតិសមាជិកបេឡាជាតិសន្តិសុខសង្គម (ប.ស.ស.) ប្រចាំឆ្នាំ (Annual National Social Security Fund Membership Statistics)",
                sub_title = "NIS 61T_13 - ស្ថិតិទីផ្សារការងារនិងកម្លាំងពលកម្ម",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}