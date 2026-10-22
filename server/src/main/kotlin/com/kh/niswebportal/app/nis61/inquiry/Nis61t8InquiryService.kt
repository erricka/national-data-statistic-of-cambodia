package com.kh.niswebportal.app.nis61.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis61t8InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_61t8"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT item_code, sector, enterprise_2025, total_employee_2025, female_employee_2025,
                       enterprise_2026, total_employee_2026, female_employee_2026
                FROM nis_61t8_enterprise_employee_stats
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
                                rs.getString("enterprise_2025") ?: "-",
                                rs.getString("total_employee_2025") ?: "-",
                                rs.getString("female_employee_2025") ?: "-",
                                rs.getString("enterprise_2026") ?: "-",
                                rs.getString("total_employee_2026") ?: "-",
                                rs.getString("female_employee_2026") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("កូដ (Code)"),
                HeaderItem("វិស័យ / Sector"),
                HeaderItem("សហគ្រាស ឆ្នាំ ២០២୫ (2025 Enterprises)"),
                HeaderItem("កម្មករនិយោជិត សរុប ២០២៥ (2025 Total Employees)"),
                HeaderItem("កម្មករនិយោជិត ស្រី ២០២៥ (2025 Female Employees)"),
                HeaderItem("សហគ្រាស ឆ្នាំ ២០២៦ (2026 Enterprises)"),
                HeaderItem("កម្មករនិយោជិត សរុប ២០២៦ (2026 Total Employees)"),
                HeaderItem("កម្មករនិយោជិត ស្រី ២០២៦ (2026 Female Employees)")
            )

            return DynamicReportResponse(
                title = "ស្ថិតិសហគ្រាស ក្រុមហ៊ុន រោងចក្រ និងកម្មករនិយោជិតប្រចាំឆ្នាំ (Annual Enterprises, Companies, Factories, and Employees Statistics)",
                sub_title = "NIS 61T_8 - ស្ថិតិទីផ្សារការងារនិងកម្លាំងពលកម្ម",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}