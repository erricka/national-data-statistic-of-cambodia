package com.kh.niswebportal.app.nis99.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis99t13InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_99t13"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT funding_source, fund_2021, fund_2022, fund_2023, fund_2024, fund_2025, fund_2026
                FROM nis_99t13_ngo_funding
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
                                rs.getString("funding_source") ?: "",
                                rs.getString("fund_2021") ?: "-",
                                rs.getString("fund_2022") ?: "-",
                                rs.getString("fund_2023") ?: "-",
                                rs.getString("fund_2024") ?: "-",
                                rs.getString("fund_2025") ?: "-",
                                rs.getString("fund_2026") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ប្រភពថវិកាអង្គការមិនមែនរដ្ឋាភិបាល (Funding Source)"),
                HeaderItem("ឆ្នាំ ២០២១ (2021) - USD Million"),
                HeaderItem("ឆ្នាំ ២០២២ (2022) - USD Million"),
                HeaderItem("ឆ្នាំ ២០២៣ (2023) - USD Million"),
                HeaderItem("ឆ្នាំ ២០២៤ (2024) - USD Million"),
                HeaderItem("ឆ្នាំ ២០២៥ (2025 Est.) - USD Million"),
                HeaderItem("ឆ្នាំ ២០២៦ (2026 Est.) - USD Million")
            )

            return DynamicReportResponse(
                title = "ការគាំទ្រថវិកាអង្គការមិនមែនរដ្ឋាភិបាលដល់ការអភិវឌ្ឍជាតិ ឆ្នាំ ២០២១-២០២៥ (NGO's Fundings Support to National Development)",
                sub_title = "NIS 99T13 - ស្ថិតិថវិកាគាំទ្រអភិវឌ្ឍន៍ជាតិពីអង្គការសង្គមស៊ីវិល/NGOs",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}