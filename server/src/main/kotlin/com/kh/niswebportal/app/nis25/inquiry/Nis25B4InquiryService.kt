package com.kh.niswebportal.app.nis25.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis25B4InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_25b4"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    province_code,
                    province_name_kh,
                    indicator_code,
                    license_category_kh,
                    year,
                    license_count
                FROM nis_25b4_driver_license_by_province_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY province_code ASC, indicator_code ASC, year ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(index++)
                        row.add(rs.getString("province_code"))
                        row.add(rs.getString("province_name_kh"))
                        row.add(rs.getString("indicator_code"))
                        row.add(rs.getString("license_category_kh"))
                        row.add(rs.getString("year"))
                        row.add(rs.getObject("license_count") ?: "-")

                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("កូដរាជធានី/ខេត្ត"),
                HeaderItem("រាជធានី ខេត្ត"),
                HeaderItem("កូដសូចនាករ"),
                HeaderItem("ប្រភេទប័ណ្ណបើកបរ"),
                HeaderItem("ឆ្នាំ"),
                HeaderItem("ចំនួនប័ណ្ណបើកបរ (ប័ណ្ណ)")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងសាធារណការ និងដឹកជញ្ជូន",
                sub_title = "25B4 ចំនួនប័ណ្ណបើកបរយានយន្តតាមរាជធានី ខេត្ត",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }
}