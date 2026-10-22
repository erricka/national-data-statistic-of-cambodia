package com.kh.niswebportal.app.nis07.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis07A6InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_07a6"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    pro_code, provinces_name, total_detainees, total_female,
                    accused_total, accused_female, accused_foreign_total, accused_foreign_female,
                    accused_juvenile_total, accused_juvenile_female, defendants_total, defendants_female,
                    defendants_foreign_total, defendants_foreign_female, defendants_juvenile_total, defendants_juvenile_female,
                    convicted_untried_total, convicted_untried_female, convicted_untried_foreign_total, convicted_untried_foreign_female,
                    convicted_untried_juvenile_total, convicted_untried_juvenile_female, convicts_total, convicts_female,
                    convicts_foreign_total, convicts_foreign_female, convicts_juvenile_total, convicts_juvenile_female
                FROM nis_07a6_detainees
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id,pro_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()
                        row.add(rs.getString("pro_code"))
                        row.add(rs.getString("provinces_name"))
                        row.add(rs.getNullableInt("total_detainees") ?: 0)
                        row.add(rs.getNullableInt("total_female") ?: 0)
                        row.add(rs.getNullableInt("accused_total") ?: 0)
                        row.add(rs.getNullableInt("accused_female") ?: 0)
                        row.add(rs.getNullableInt("accused_foreign_total") ?: 0)
                        row.add(rs.getNullableInt("accused_foreign_female") ?: 0)
                        row.add(rs.getNullableInt("accused_juvenile_total") ?: 0)
                        row.add(rs.getNullableInt("accused_juvenile_female") ?: 0)
                        row.add(rs.getNullableInt("defendants_total") ?: 0)
                        row.add(rs.getNullableInt("defendants_female") ?: 0)
                        row.add(rs.getNullableInt("defendants_foreign_total") ?: 0)
                        row.add(rs.getNullableInt("defendants_foreign_female") ?: 0)
                        row.add(rs.getNullableInt("defendants_juvenile_total") ?: 0)
                        row.add(rs.getNullableInt("defendants_juvenile_female") ?: 0)
                        row.add(rs.getNullableInt("convicted_untried_total") ?: 0)
                        row.add(rs.getNullableInt("convicted_untried_female") ?: 0)
                        row.add(rs.getNullableInt("convicted_untried_foreign_total") ?: 0)
                        row.add(rs.getNullableInt("convicted_untried_foreign_female") ?: 0)
                        row.add(rs.getNullableInt("convicted_untried_juvenile_total") ?: 0)
                        row.add(rs.getNullableInt("convicted_untried_juvenile_female") ?: 0)
                        row.add(rs.getNullableInt("convicts_total") ?: 0)
                        row.add(rs.getNullableInt("convicts_female") ?: 0)
                        row.add(rs.getNullableInt("convicts_foreign_total") ?: 0)
                        row.add(rs.getNullableInt("convicts_foreign_female") ?: 0)
                        row.add(rs.getNullableInt("convicts_juvenile_total") ?: 0)
                        row.add(rs.getNullableInt("convicts_juvenile_female") ?: 0)
                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"), HeaderItem("ពន្ធនាគារ"),
                HeaderItem("សរុបរួម"), HeaderItem(""),
                HeaderItem("ជនត្រូវចោទ"), HeaderItem(""), HeaderItem(""), HeaderItem(""), HeaderItem(""), HeaderItem(""),
                HeaderItem("ជនជាប់ចោទ"), HeaderItem(""), HeaderItem(""), HeaderItem(""), HeaderItem(""), HeaderItem(""),
                HeaderItem("ពិរុទ្ធជន"), HeaderItem(""), HeaderItem(""), HeaderItem(""), HeaderItem(""), HeaderItem(""),
                HeaderItem("ទណ្ឌិត"), HeaderItem(""), HeaderItem(""), HeaderItem(""), HeaderItem(""), HeaderItem("")
            )

            val subHeader = listOf(
                HeaderItem(""), HeaderItem(""),
                HeaderItem("រួម"), HeaderItem("ស្រី"),
                HeaderItem("រួម"), HeaderItem("ស្រី"), HeaderItem("បរទេស"), HeaderItem("ស្រី"), HeaderItem("អនីតិជន"), HeaderItem("ស្រី"),
                HeaderItem("រួម"), HeaderItem("ស្រី"), HeaderItem("បរទេស"), HeaderItem("ស្រី"), HeaderItem("អនីតិជន"), HeaderItem("ស្រី"),
                HeaderItem("រួម"), HeaderItem("ស្រី"), HeaderItem("បរទេស"), HeaderItem("ស្រី"), HeaderItem("អនីតិជន"), HeaderItem("ស្រី"),
                HeaderItem("រួម"), HeaderItem("ស្រី"), HeaderItem("បរទេស"), HeaderItem("ស្រី"), HeaderItem("អនីតិជន"), HeaderItem("ស្រី")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងមហាផ្ទៃ",
                sub_title = "07A6 ស្ថិតិជនជាប់ឃុំប្រចាំឆ្នាំ",
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