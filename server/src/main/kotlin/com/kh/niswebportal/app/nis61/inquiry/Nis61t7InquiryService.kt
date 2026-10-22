package com.kh.niswebportal.app.nis61.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis61t7InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_61t7"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT item_code, description, unit, total_2025, female_2025, total_2026, female_2026
                FROM nis_61t7_public_employment_services
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
                                rs.getString("description") ?: "",
                                rs.getString("unit") ?: "",
                                rs.getString("total_2025") ?: "-",
                                rs.getString("female_2025") ?: "-",
                                rs.getString("total_2026") ?: "-",
                                rs.getString("female_2026") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("កូដ (Code)"),
                HeaderItem("បរិយាយ (Description)"),
                HeaderItem("ឯកតា / កម្រិត (Unit)"),
                HeaderItem("សរុប ឆ្នាំ ២០២៥ (2025 Total)"),
                HeaderItem("ស្រី ឆ្នាំ ២០២៥ (2025 Female)"),
                HeaderItem("សរុប ឆ្នាំ ២០២៦ (2026 Total)"),
                HeaderItem("ស្រី ឆ្នាំ ២០២៦ (2026 Female)")
            )

            return DynamicReportResponse(
                title = "ស្ថិតិការផ្តល់សេវាការងារសាធារណៈ ប្រចាំឆ្នាំ (Annual Public Employment Services Statistics)",
                sub_title = "NIS 61T_7 - ស្ថិតិទីផ្សារការងារនិងកម្លាំងពលកម្ម",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}