package com.kh.niswebportal.app.nis99.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis99t12InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_99t12"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT hq_country, own_fund_2022, own_fund_2023, own_fund_2024, own_fund_2025
                FROM nis_99t12_fngo_disbursement
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
                                rs.getString("hq_country") ?: "",
                                rs.getString("own_fund_2022") ?: "-",
                                rs.getString("own_fund_2023") ?: "-",
                                rs.getString("own_fund_2024") ?: "-",
                                rs.getString("own_fund_2025") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ទីស្នាក់ការកណ្តាលប្រទេស (HQ Country)"),
                HeaderItem("ថវិកាផ្ទាល់ខ្លួន ឆ្នាំ ២០២២ (Own Fund 2022) - US$"),
                HeaderItem("ថវិកាផ្ទាល់ខ្លួន ឆ្នាំ ២០២୩ (Own Fund 2023) - US$"),
                HeaderItem("ថវិកាផ្ទាល់ខ្លួន ឆ្នាំ ២០២៤ (Own Fund 2024) - US$"),
                HeaderItem("ថវិកាផ្ទាល់ខ្លួន ឆ្នាំ ២០២៥ (Own Fund 2025) - US$")
            )

            return DynamicReportResponse(
                title = "ការចំណាយថវិកាអង្គការមិនមែនរដ្ឋាភិបាលបរទេស តាមប្រទេស (Disbursement Foreign NGOs by Countries)",
                sub_title = "NIS 99T12 - ស្ថិតិថវិកា disbursement NGO បរទេសតាមប្រទេស",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}