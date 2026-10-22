package com.kh.niswebportal.app.nis12.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis12A17InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_12a17"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    item_code,
                    item_name_kh,
                    year,
                    amount_million_khr,
                    percentage,
                    others
                FROM nis_12a17_health_revenue_report
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val row = listOf<Any?>(
                            rs.getString("item_code"),
                            rs.getString("item_name_kh"),
                            rs.getInt("year"),
                            rs.getNullableDouble("amount_million_khr"),
                            rs.getNullableDouble("percentage"),
                            rs.getString("others")
                        )
                        rows.add(row)
                    }
                }
            }

            val headers = listOf(
                HeaderItem("កូដ (Code)"),
                HeaderItem("បរិយាយខ្ទង់ចំណូល"),
                HeaderItem("ឆ្នាំ"),
                HeaderItem("ឯកតាគិតជាលានរៀល"),
                HeaderItem("គិតជា%"),
                HeaderItem("ផ្សេងៗ")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងសុខាភិបាល",
                sub_title = "12a17 របាយការណ៍ចំណូលថវិកាសុខាភិបាល",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }

    
}