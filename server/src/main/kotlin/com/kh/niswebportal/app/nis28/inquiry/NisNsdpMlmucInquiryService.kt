package com.kh.niswebportal.app.nis28.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class NisNsdpMlmucInquiryService(
    private val dataSource: DataSource
):NisInquiryStrategy {

    override val sourceId: String = "nis_nsdp_mlmuc"

    override fun getDynamicReport(uploadId: String? ): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    indicator_no,
                    indicator_name_kh,
                    unit,
                    year,
                    target_val,
                    actual_val,
                    is_additional_indicator
                FROM nis_nsdp_mlmuc
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY CAST(NULLIF(indicator_no, '') AS INTEGER) ASC NULLS LAST, year ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(index++)
                        row.add(rs.getString("indicator_no"))
                        row.add(rs.getString("indicator_name_kh"))
                        row.add(rs.getString("unit"))
                        row.add(rs.getInt("year"))
                        row.add(rs.getNullableDouble("target_val") ?: 0.0)
                        row.add(rs.getNullableDouble("actual_val") ?: 0.0)
                        row.add(if (rs.getBoolean("is_additional_indicator")) "បន្ថែម" else "សូចនាករគន្លឹះ")

                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("លេខសូចនាករ"),
                HeaderItem("សូចនាករ"),
                HeaderItem("ឯកតា"),
                HeaderItem("ឆ្នាំ"),
                HeaderItem("គោលដៅ ផ.យ.អ.ជ"),
                HeaderItem("លទ្ធផលសម្រេចបាន"),
                HeaderItem("ប្រភេទសូចនាករ")
            )

            return DynamicReportResponse(
                title = "ការគ្រប់គ្រងនគរូបនីយកម្ម",
                sub_title = "តារាងសូចនាករ៖ វឌ្ឍនភាពនៃការសម្រេចបាននៃការអនុវត្តផែនការយុទ្ធសាស្ត្រអភិវឌ្ឍន៍ជាតិ",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }

    
}