package com.kh.niswebportal.app.nis99.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis99t1InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_99t1"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT data_type, year_period, all_projects_count, all_project_cost, all_working_capital, all_fixed_asset,
                       active_projects_count, active_project_cost, active_working_capital, active_fixed_asset,
                       former_projects_count, former_project_cost, former_working_capital, former_fixed_asset,
                       non_active_projects_count, non_active_project_cost, non_active_working_capital, non_active_fixed_asset
                FROM nis_99t1_investment_project_approvals
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
                                rs.getString("all_projects_count") ?: "-",
                                rs.getString("all_project_cost") ?: "-",
                                rs.getString("all_working_capital") ?: "-",
                                rs.getString("all_fixed_asset") ?: "-",
                                rs.getString("active_projects_count") ?: "-",
                                rs.getString("active_project_cost") ?: "-",
                                rs.getString("active_working_capital") ?: "-",
                                rs.getString("active_fixed_asset") ?: "-",
                                rs.getString("former_projects_count") ?: "-",
                                rs.getString("former_project_cost") ?: "-",
                                rs.getString("former_working_capital") ?: "-",
                                rs.getString("former_fixed_asset") ?: "-",
                                rs.getString("non_active_projects_count") ?: "-",
                                rs.getString("non_active_project_cost") ?: "-",
                                rs.getString("non_active_working_capital") ?: "-",
                                rs.getString("non_active_fixed_asset") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ឆ្នាំ / ស្ថិតិ"),
                HeaderItem("សរុបគម្រោង"),
                HeaderItem("ចំណាយគម្រោងសរុប"),
                HeaderItem("ទុនបង្វិលសរុប"),
                HeaderItem("ទ្រព្យសកម្មអចិន្ត្រៃយ៍សរុប"),
                HeaderItem("សកម្ម (គម្រោង)"),
                HeaderItem("សកម្ម (ចំណាយ)"),
                HeaderItem("សកម្ម (ទុនបង្វិល)"),
                HeaderItem("សកម្ម (ទ្រព្យអចិន្ត្រៃយ៍)"),
                HeaderItem("អតីតសកម្ម (គម្រោង)"),
                HeaderItem("អតីតសកម្ម (ចំណាយ)"),
                HeaderItem("អតីតសកម្ម (ទុនបង្វិល)"),
                HeaderItem("អតីតសកម្ម (ទ្រព្យអចិន្ត្រៃយ៍)"),
                HeaderItem("អសកម្ម (គម្រោង)"),
                HeaderItem("អសកម្ម (ចំណាយ)"),
                HeaderItem("អសកម្ម (ទុនបង្វិល)"),
                HeaderItem("អសកម្ម (ទ្រព្យអចិន្ត្រៃយ៍)")
            )

            return DynamicReportResponse(
                title = "ចំនួនគម្រោងដែលបានអនុម័ត (All investment project approvals)",
                sub_title = "NIS 99T1 - ស្ថិតិគម្រោងវិនិយោគ",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}