package com.kh.niswebportal.app.nis61.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis61t6InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_61t6"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT item_code, sector, year_2025_value, year_2026_value, occupation, occupation_value
                FROM nis_61t6_labor_market_status
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
                                rs.getString("item_code") ?: "",
                                rs.getString("sector") ?: "",
                                rs.getString("year_2025_value") ?: "-",
                                rs.getString("year_2026_value") ?: "-",
                                rs.getString("occupation") ?: "-",
                                rs.getString("occupation_value") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("កូដ (Code)"),
                HeaderItem("វិស័យ / Sector"),
                HeaderItem("ឆ្នាំ ២០២៥ (2025)"),
                HeaderItem("ឆ្នាំ ២០២៦ (2026)"),
                HeaderItem("មុខតំណែង / Occupation"),
                HeaderItem("តម្លៃមុខតំណែង (Occupation Value)")
            )

            return DynamicReportResponse(
                title = "ស្ថិតិស្ថានភាពទីផ្សារការងារ និងកម្លាំងពលកម្ម ប្រចាំឆ្នាំ (Annual Labor Market and Workforce Status Statistics)",
                sub_title = "NIS 61T_6 - ស្ថិតិទីផ្សារការងារនិងកម្លាំងពលកម្ម",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}