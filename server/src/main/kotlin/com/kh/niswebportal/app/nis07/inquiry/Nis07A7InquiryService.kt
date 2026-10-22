package com.kh.niswebportal.app.nis07.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis07A7InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_07a7"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    pro_code, provinces_name, num_households, num_persons_total, num_persons_female,
                    age_18_over_total, age_18_over_female, num_nationalities
                FROM nis_07a7_immigrant_foreigners
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
                        row.add(rs.getNullableInt("num_households") ?: 0)
                        row.add(rs.getNullableInt("num_persons_total") ?: 0)
                        row.add(rs.getNullableInt("num_persons_female") ?: 0)
                        row.add(rs.getNullableInt("age_18_over_total") ?: 0)
                        row.add(rs.getNullableInt("age_18_over_female") ?: 0)
                        row.add(rs.getNullableInt("num_nationalities") ?: 0)
                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"), HeaderItem("រាជធានី ខេត្ត"), HeaderItem("ចំនួនគ្រួសារ"),
                HeaderItem("ចំនួនមនុស្ស"), HeaderItem(""),
                HeaderItem("អាយុ ១៨ ឆ្នាំឡើង"), HeaderItem(""),
                HeaderItem("ចំនួនជនជាតិ")
            )

            val subHeader = listOf(
                HeaderItem(""), HeaderItem(""), HeaderItem(""),
                HeaderItem("សរុបរួម"), HeaderItem("ស្រី"),
                HeaderItem("ចំនួនរួម"), HeaderItem("ស្រី"),
                HeaderItem("")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងមហាផ្ទៃ",
                sub_title = "07A7 ស្ថិតិជនបរទេសអន្តោប្រវេសន្តប្រចាំឆ្នាំ",
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