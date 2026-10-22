package com.kh.niswebportal.app.nis99.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis99t3InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_99t3"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT year_period, 
                       agri_projects_count, agri_total_cost, agri_working_capital, agri_fixed_asset,
                       ind_projects_count, ind_total_cost, ind_working_capital, ind_fixed_asset,
                       infra_projects_count, infra_total_cost, infra_working_capital, infra_fixed_asset,
                       tour_projects_count, tour_total_cost, tour_working_capital, tour_fixed_asset
                FROM nis_99t3_sector_capital
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
                                // Agriculture
                                rs.getString("agri_projects_count") ?: "-",
                                rs.getString("agri_total_cost") ?: "-",
                                rs.getString("agri_working_capital") ?: "-",
                                rs.getString("agri_fixed_asset") ?: "-",
                                // Industry
                                rs.getString("ind_projects_count") ?: "-",
                                rs.getString("ind_total_cost") ?: "-",
                                rs.getString("ind_working_capital") ?: "-",
                                rs.getString("ind_fixed_asset") ?: "-",
                                // Infrastructure/Services
                                rs.getString("infra_projects_count") ?: "-",
                                rs.getString("infra_total_cost") ?: "-",
                                rs.getString("infra_working_capital") ?: "-",
                                rs.getString("infra_fixed_asset") ?: "-",
                                // Tourism
                                rs.getString("tour_projects_count") ?: "-",
                                rs.getString("tour_total_cost") ?: "-",
                                rs.getString("tour_working_capital") ?: "-",
                                rs.getString("tour_fixed_asset") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ឆ្នាំ"),
                HeaderItem("កសិកម្ម - គម្រោង"), HeaderItem("កសិកម្ម - ចំណាយសរុប"), HeaderItem("កសិកម្ម - ដើមទុនបង្វិល"), HeaderItem("កសិកម្ម - ទ្រព្យអចិន្ត្រៃយ៍"),
                HeaderItem("ឧស្សាហកម្ម - គម្រោង"), HeaderItem("ឧស្សាហកម្ម - ចំណាយសរុប"), HeaderItem("ឧស្សាហកម្ម - ដើមទុនបង្វិល"), HeaderItem("ឧស្សាហកម្ម - ទ្រព្យអចិន្ត្រៃយ៍"),
                HeaderItem("រចនាសម្ព័ន្ធសេដ្ឋកិច្ច/សេវាកម្ម - គម្រោង"), HeaderItem("រចនាសម្ព័ន្ធសេដ្ឋកិច្ច/សេវាកម្ម - ចំណាយសរុប"), HeaderItem("រចនាសម្ព័ន្ធសេដ្ឋកិច្ច/សេវាកម្ម - ដើមទុនបង្វិល"), HeaderItem("រចនាសម្ព័ន្ធសេដ្ឋកិច្ច/សេវាកម្ម - ទ្រព្យអចិន្ត្រៃយ៍"),
                HeaderItem("ទេសចរណ៍ - គម្រោង"), HeaderItem("ទេសចរណ៍ - ចំណាយសរុប"), HeaderItem("ទេសចរណ៍ - ដើមទុនបង្វិល"), HeaderItem("ទេសចរណ៍ - ទ្រព្យអចិន្ត្រៃយ៍")
            )

            return DynamicReportResponse(
                title = "ការវិភាគទុនវិនិយោគ តាមវិស័យនៃក្រុមហ៊ុនសកម្ម (Analysis of capital by sector of active companies)",
                sub_title = "NIS 99T3 - ស្ថិតិវិភាគទុនវិនិយោគតាមវិស័យ",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}