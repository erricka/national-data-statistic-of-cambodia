package com.kh.niswebportal.app.nis99.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis99t6InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_99t6"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT year_period, agri_capital, industry_capital, infra_capital, tourism_capital, total_capital,
                       agri_share, industry_share, infra_share, tourism_share, total_share,
                       agri_growth, industry_growth, infra_growth, tourism_growth, total_growth
                FROM nis_99t6_sector_capital
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
                                rs.getString("agri_capital") ?: "-",
                                rs.getString("industry_capital") ?: "-",
                                rs.getString("infra_capital") ?: "-",
                                rs.getString("tourism_capital") ?: "-",
                                rs.getString("total_capital") ?: "-",
                                rs.getString("agri_share") ?: "-",
                                rs.getString("industry_share") ?: "-",
                                rs.getString("infra_share") ?: "-",
                                rs.getString("tourism_share") ?: "-",
                                rs.getString("total_share") ?: "-",
                                rs.getString("agri_growth") ?: "-",
                                rs.getString("industry_growth") ?: "-",
                                rs.getString("infra_growth") ?: "-",
                                rs.getString("tourism_growth") ?: "-",
                                rs.getString("total_growth") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ឆ្នាំ"),
                HeaderItem("កសិកម្ម (Agriculture) - US$"),
                HeaderItem("ឧស្សាហកម្ម (Industry) - US$"),
                HeaderItem("សេវាហេដ្ឋារចនាសម្ព័ន្ធ (Infrastructure) - US$"),
                HeaderItem("ទេសចរណ៍ (Tourism) - US$"),
                HeaderItem("សរុប (Total) - US$"),
                HeaderItem("កសិកម្ម (%)"),
                HeaderItem("ឧស្សាហកម្ម (%)"),
                HeaderItem("សេវាហេដ្ឋារចនាសម្ព័ន្ធ (%)"),
                HeaderItem("ទេសចរណ៍ (%)"),
                HeaderItem("សរុប (%)"),
                HeaderItem("កសិកម្ម (Growth %)"),
                HeaderItem("ឧស្សាហកម្ម (Growth %)"),
                HeaderItem("សេវាហេដ្ឋារចនាសម្ព័ន្ធ (Growth %)"),
                HeaderItem("ទេសចរណ៍ (Growth %)"),
                HeaderItem("សរុប (Growth %)")
            )

            return DynamicReportResponse(
                title = "គម្រោងវិនិយោគទុកដែលបានអនុម័ត (ទុនបានចុះបញ្ជី) តាមវិស័យ (Investment projects approved by sector)",
                sub_title = "NIS 99T6 - ស្ថិតិគម្រោងវិនិយោគតាមវិស័យ",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}