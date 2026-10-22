package com.kh.niswebportal.app.nis25.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis25C1InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_25c1"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    year,
                    indicator_code,
                    severity_category_kh,
                    gender_kh,
                    victim_count
                FROM nis_25c1_road_accident_victims_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY year ASC, indicator_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(index++)
                        row.add(rs.getString("year"))
                        row.add(rs.getString("indicator_code"))
                        row.add(rs.getString("severity_category_kh"))
                        row.add(rs.getString("gender_kh"))
                        row.add(rs.getObject("victim_count") ?: "-")

                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ឆ្នាំ"),
                HeaderItem("កូដសូចនាករ"),
                HeaderItem("កម្រិតគ្រោះថ្នាក់"),
                HeaderItem("ភេទ"),
                HeaderItem("ចំនួនជនរងគ្រោះ (នាក់)")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងសាធារណការ និងដឹកជញ្ជូន",
                sub_title = "25C1 ចំនួនជនរងគ្រោះថ្នាក់ចរាចរណ៍ផ្លូវគោក តាមកម្រិតគ្រោះថ្នាក់ និងភេទ",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }
}