package com.kh.niswebportal.app.nis99.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis99t4InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_99t4"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT year_period, japan, usa, eu_total, east_asia_korea, east_asia_china, east_asia_other, east_asia_total, asean, other_countries, total_capital
                FROM nis_99t4_country_capital
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
                                rs.getString("japan") ?: "-",
                                rs.getString("usa") ?: "-",
                                rs.getString("eu_total") ?: "-",
                                rs.getString("east_asia_korea") ?: "-",
                                rs.getString("east_asia_china") ?: "-",
                                rs.getString("east_asia_other") ?: "-",
                                rs.getString("east_asia_total") ?: "-",
                                rs.getString("asean") ?: "-",
                                rs.getString("other_countries") ?: "-",
                                rs.getString("total_capital") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ឆ្នាំ"),
                HeaderItem("ជប៉ុន (Japan)"),
                HeaderItem("សហរដ្ឋអាមេរិក (USA)"),
                HeaderItem("សហភាពអឺរ៉ុប (European Union)"),
                HeaderItem("អាស៊ីបូព៌ា - កូរ៉េ"),
                HeaderItem("អាស៊ីបូព៌ា - ចិន"),
                HeaderItem("អាស៊ីបូព៌ា - ផ្សេងៗ"),
                HeaderItem("អាស៊ីបូព៌ា - សរុប"),
                HeaderItem("អាស៊ាន (ASEAN)"),
                HeaderItem("ប្រទេសដទៃទៀត (Other countries)"),
                HeaderItem("សរុប (Total)")
            )

            return DynamicReportResponse(
                title = "គម្រោងវិនិយោគដែលបានអនុម័ត (ទុនបានចុះបញ្ជី) តាមប្រទេស (All investment projects approved by broad country)",
                sub_title = "NIS 99T4 - ស្ថិតិគម្រោងវិនិយោគតាមប្រទេស",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}