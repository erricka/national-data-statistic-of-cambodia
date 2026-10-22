package com.kh.niswebportal.app.nis28.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis28B4InquiryService(
    private val dataSource: DataSource
):NisInquiryStrategy {

    override val sourceId: String = "nis_28b4"

    override fun getDynamicReport(uploadId: String? ): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    year,
                    province_code,
                    province_name_kh,
                    unit,
                    in_process,
                    completed,
                    approved,
                    total_area_km2
                FROM nis_28b4_commune_land_use_plan
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY year ASC, province_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(index++)
                        row.add(rs.getInt("year"))
                        row.add(rs.getString("province_code"))
                        row.add(rs.getString("province_name_kh"))
                        row.add(rs.getString("unit"))
                        row.add(rs.getNullableDouble("in_process") ?: 0.0)
                        row.add(rs.getNullableDouble("completed") ?: 0.0)
                        row.add(rs.getNullableDouble("approved") ?: 0.0)
                        row.add(rs.getNullableDouble("total_area_km2") ?: 0.0)

                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ឆ្នាំ"),
                HeaderItem("កូដ"),
                HeaderItem("រាជធានី/ខេត្ត"),
                HeaderItem("ខ្នាត"),
                HeaderItem("កំពុងដំណើរការ"),
                HeaderItem("បញ្ចប់"),
                HeaderItem("អនុម័ត"),
                HeaderItem("ទំហំ (គម២)")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ស្ថិតិវិស័យរៀបចំដែនដី នគរូបនីយកម្ម និងសំណង់",
                sub_title = "28B4 ផែនការប្រើប្រាស់ដីនៅក្នុងឃុំសង្កាត់",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }

    
}