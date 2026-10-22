package com.kh.niswebportal.app.nis99.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis99t2InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_99t2"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT data_type, year_period, active_projects_count, active_project_cost, active_fixed_asset, active_equity,
                       accum_projects_count, accum_project_cost, accum_fixed_asset, accum_equity
                FROM nis_99t2_active_companies
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(dataSql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var idx = 1
                    while (rs.next()) {
                        val dataType = rs.getString("data_type")
                        val prefix = if (dataType == "ABSOLUTE_CHANGE") "[ផ្លាស់ប្តូរ] " else ""

                        rows.add(
                            listOf(
                                idx++,
                                prefix + (rs.getString("year_period") ?: ""),
                                rs.getString("active_projects_count") ?: "-",
                                rs.getString("active_project_cost") ?: "-",
                                rs.getString("active_fixed_asset") ?: "-",
                                rs.getString("active_equity") ?: "-",
                                rs.getString("accum_projects_count") ?: "-",
                                rs.getString("accum_project_cost") ?: "-",
                                rs.getString("accum_fixed_asset") ?: "-",
                                rs.getString("accum_equity") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ឆ្នាំ / ស្ថិតិ"),
                HeaderItem("ក្រុមហ៊ុនមានសកម្មភាព (គម្រោង)"),
                HeaderItem("ចំណាយគម្រោង ១"),
                HeaderItem("ចំណាយគម្រោង (ទ្រព្យសកម្មអចិន្ត្រៃយ៍)"),
                HeaderItem("មូលធនភាគហ៊ុន ៣"),
                HeaderItem("ការប្រមូលផ្តុំក្រុមហ៊ុនមានសកម្មភាព (គម្រោង)"),
                HeaderItem("ចំណាយគម្រោង"),
                HeaderItem("ទ្រព្យសកម្មអចិន្ត្រៃយ៍"),
                HeaderItem("មូលធនភាគហ៊ុន")
            )

            return DynamicReportResponse(
                title = "ក្រុមហ៊ុន/គម្រោងដែលមានសកម្មភាព (All active companies/projects)",
                sub_title = "NIS 99T2 - ស្ថិតិគម្រោងវិនិយោគមានសកម្មភាព",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}