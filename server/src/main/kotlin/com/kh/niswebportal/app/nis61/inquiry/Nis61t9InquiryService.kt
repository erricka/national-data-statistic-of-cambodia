package com.kh.niswebportal.app.nis61.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis61t9InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_61t9"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT item_code, description, sub_description, unit, value_2025, value_2026
                FROM nis_61t9_professional_relations
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(dataSql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var idx = 1
                    while (rs.next()) {
                        val mainDesc = rs.getString("description") ?: ""
                        val subDesc = rs.getString("sub_description")
                        val fullDesc = if (!subDesc.isNullOrBlank()) "$mainDesc - $subDesc" else mainDesc

                        rows.add(
                            listOf(
                                idx++,
                                rs.getString("item_code") ?: "",
                                fullDesc,
                                rs.getString("unit") ?: "",
                                rs.getString("value_2025") ?: "-",
                                rs.getString("value_2026") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("កូដ (Code)"),
                HeaderItem("បរិយាយ (Description)"),
                HeaderItem("ឯកតា (Unit)"),
                HeaderItem("ឆ្នាំ ២០២៥ (2025)"),
                HeaderItem("ឆ្នាំ ២០២៦ (2026)")
            )

            return DynamicReportResponse(
                title = "ស្ថិតិទំនាក់ទំនងវិជ្ជាជីវៈ និងការដោះស្រាយវិវាទការងារប្រចាំឆ្នាំ (Annual Professional Relations and Labor Dispute Settlement Statistics)",
                sub_title = "NIS 61T_9 - ស្ថិតិទីផ្សារការងារនិងកម្លាំងពលកម្ម",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}