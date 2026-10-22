package com.kh.niswebportal.app.nis19.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis19a1InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_19a1"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    category_code,
                    category_name,
                    location,
                    physiographic_region,
                    park_area_thousand_ha,
                    forest_cover_area_thousand_ha,
                    salient_features
                FROM nis_19a1_protected_areas
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY category_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(index++)
                        row.add(rs.getString("category_code"))
                        row.add(rs.getString("category_name"))
                        row.add(rs.getString("location") ?: "-")
                        row.add(rs.getString("physiographic_region") ?: "-")
                        row.add(rs.getString("park_area_thousand_ha") ?: "-")
                        row.add(rs.getString("forest_cover_area_thousand_ha") ?: "-")
                        row.add(rs.getString("salient_features") ?: "-")

                        rows.add(row)
                    }
                }
            }

            val header = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("កូដ"),
                HeaderItem("Protected Areas"),
                HeaderItem("Location"),
                HeaderItem("Physiographic Region"),
                HeaderItem("Park Area ('000 ha)"),
                HeaderItem("Forest Cover Area ('000 ha)"),
                HeaderItem("Salient Features")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងបរិស្ថាន",
                sub_title = "19A1 តំបន់ការពារធម្មជាតិ និងផ្ទៃដីគម្របព្រៃឈើ (Protected areas and forest cover)",
                tb_headers = listOf(header),
                tb_rows = rows
            )
        }
    }
}