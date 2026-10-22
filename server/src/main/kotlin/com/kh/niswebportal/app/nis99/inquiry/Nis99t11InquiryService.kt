package com.kh.niswebportal.app.nis99.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis99t11InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_99t11"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT year_period, sector, manager, engineer, technician, supervisor, staff, skilled_labor, unskilled_labor, total_labor
                FROM nis_99t11_investment_labor
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
                                rs.getString("year_period") ?: "",
                                rs.getString("sector") ?: "",
                                rs.getString("manager") ?: "-",
                                rs.getString("engineer") ?: "-",
                                rs.getString("technician") ?: "-",
                                rs.getString("supervisor") ?: "-",
                                rs.getString("staff") ?: "-",
                                rs.getString("skilled_labor") ?: "-",
                                rs.getString("unskilled_labor") ?: "-",
                                rs.getString("total_labor") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ឆ្នាំ"),
                HeaderItem("វិស័យ (Sector)"),
                HeaderItem("អ្នកគ្រប់គ្រង (Manager)"),
                HeaderItem("វិស្វករ (Engineer)"),
                HeaderItem("បច្ចេកទេស (Technician)"),
                HeaderItem("អ្នកគ្រប់គ្រងរង/ត្រួតពិនិត្យ (Supervisor)"),
                HeaderItem("បុគ្គលិក (Staff)"),
                HeaderItem("ពលកម្មជំនាញ (Skilled Labor)"),
                HeaderItem("ពលកម្មអត់ជំនាញ (Unskilled Labor)"),
                HeaderItem("សរុប (Total Labor)")
            )

            return DynamicReportResponse(
                title = "កម្លាំងពលកម្ម តាមវិស័យនៃគម្រោងវិនិយោគដែលបានអនុម័ត (Labor by Sector of Approved Investment Projects)",
                sub_title = "NIS 99T11 - ស្ថិតិកម្លាំងពលកម្មតាមវិស័យ",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}