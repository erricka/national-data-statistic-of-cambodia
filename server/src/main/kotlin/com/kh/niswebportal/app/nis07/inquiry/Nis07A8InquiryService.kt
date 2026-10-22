package com.kh.niswebportal.app.nis07.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis07A8InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_07a8"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    category_code, category_name_kh, y0_num, y0_estab, y0_register,
                    y1_new, y1_register, y1_delete, end_year, grand_total
                FROM nis_07a8_political_parties
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY category_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()
                        row.add(rs.getString("category_code"))
                        row.add(rs.getString("category_name_kh"))
                        row.add(rs.getNullableInt("y0_num") ?: 0)
                        row.add(rs.getNullableInt("y0_estab") ?: 0)
                        row.add(rs.getNullableInt("y0_register") ?: 0)
                        row.add(rs.getNullableInt("y1_new") ?: 0)
                        row.add(rs.getNullableInt("y1_register") ?: 0)
                        row.add(rs.getNullableInt("y1_delete") ?: 0)
                        row.add(rs.getNullableInt("end_year") ?: 0)
                        row.add(rs.getNullableInt("grand_total") ?: 0)
                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"), HeaderItem("ស្ថានភាព"),
                HeaderItem("អំពីស្ថានភាពដើមឆ្នាំ"), HeaderItem(""), HeaderItem(""),
                HeaderItem("ស្ថានភាពប្រែប្រួលនៅក្នុងឆ្នាំ"), HeaderItem(""), HeaderItem(""),
                HeaderItem("ចំនួនសរុបចុងឆ្នាំ"), HeaderItem("សរុបរួម")
            )

            val subHeader = listOf(
                HeaderItem(""), HeaderItem(""),
                HeaderItem("ដើមឆ្នាំ"), HeaderItem("បង្កើត"), HeaderItem("ចុះបញ្ជី"),
                HeaderItem("បង្កើតថ្មី"), HeaderItem("បានចុះបញ្ជី"), HeaderItem("លុបឈ្មោះ"),
                HeaderItem("ចុងឆ្នាំ"), HeaderItem("")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងមហាផ្ទៃ",
                sub_title = "07A8 ស្ថិតិគណបក្សនយោបាយប្រចាំឆ្នាំ",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = rows
            )
        }
    }

    private fun ResultSet.getNullableInt(col: String): Int? {
        val v = this.getInt(col)
        return if (this.wasNull()) null else v
    }
}