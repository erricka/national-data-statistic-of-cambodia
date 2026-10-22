package com.kh.niswebportal.app.nis99.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis99t10InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_99t10"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT year_period, sector, country_china, country_singapore, country_cambodia, country_usa, country_vietnam,
                       country_cayman_islands, country_british, country_japan, country_marshall_islands, country_canada,
                       country_saint_lucia, country_australia, total_capital
                FROM nis_99t10_cdc_sez
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
                                rs.getString("country_china") ?: "-",
                                rs.getString("country_singapore") ?: "-",
                                rs.getString("country_cambodia") ?: "-",
                                rs.getString("country_usa") ?: "-",
                                rs.getString("country_vietnam") ?: "-",
                                rs.getString("country_cayman_islands") ?: "-",
                                rs.getString("country_british") ?: "-",
                                rs.getString("country_japan") ?: "-",
                                rs.getString("country_marshall_islands") ?: "-",
                                rs.getString("country_canada") ?: "-",
                                rs.getString("country_saint_lucia") ?: "-",
                                rs.getString("country_australia") ?: "-",
                                rs.getString("total_capital") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ឆ្នាំ"),
                HeaderItem("វិស័យ (Sector)"),
                HeaderItem("ចិន (China) - US$"),
                HeaderItem("សិង្ហបុរី (Singapore) - US$"),
                HeaderItem("កម្ពុជា (Cambodia) - US$"),
                HeaderItem("សហរដ្ឋអាមេរិក (USA) - US$"),
                HeaderItem("វៀតណាម (Vietnam) - US$"),
                HeaderItem("កៃម៉ែនអាយលែន (Cayman Islands) - US$"),
                HeaderItem("ប៊ីទីស (British) - US$"),
                HeaderItem("ជប៉ុន (Japan) - US$"),
                HeaderItem("ម៉ាហ្សាល់អាយលែន (Marshall Islands) - US$"),
                HeaderItem("កាណាដា (Canada) - US$"),
                HeaderItem("សាំងលូស៊ី (Saint Lucia) - US$"),
                HeaderItem("អូស្ត្រាលី (Australia) - US$"),
                HeaderItem("សរុប (Total) - US$")
            )

            return DynamicReportResponse(
                title = "គម្រោងវិនិយោគដែលបានអនុម័ត តាមខេត្ត/តំបន់សេដ្ឋកិច្ចពិសេស (CDC SEZ Investment Projects by Country and Sector)",
                sub_title = "NIS 99T10 - ស្ថិតិគម្រោងវិនិយោគតំបន់សេដ្ឋកិច្ចពិសេស CDC",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}