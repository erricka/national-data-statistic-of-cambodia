package com.kh.niswebportal.app.nis19.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis19a2InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_19a2"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    province_code,
                    province_name,
                    land_forest_2016,
                    land_forest_2018,
                    land_forest_2020,
                    land_forest_2022,
                    land_forest_2024,
                    land_forest_2026,
                    forest_cover_2016,
                    forest_cover_2018,
                    forest_cover_2020,
                    forest_cover_2022,
                    forest_cover_2024,
                    forest_cover_2026
                FROM nis_19a2_province_forest_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY CASE WHEN province_code = 'TOTAL' THEN 2 ELSE 1 END,
                         CAST(NULLIF(regexp_replace(province_code, '\D', '', 'g'), '') AS INTEGER) ASC NULLS LAST
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val code = rs.getString("province_code")
                        val row = mutableListOf<Any?>()

                        row.add(if (code == "TOTAL") "-" else index++)
                        row.add(rs.getString("province_name"))

                        row.add(rs.getString("land_forest_2016") ?: "-")
                        row.add(rs.getString("land_forest_2018") ?: "-")
                        row.add(rs.getString("land_forest_2020") ?: "-")
                        row.add(rs.getString("land_forest_2022") ?: "-")
                        row.add(rs.getString("land_forest_2024") ?: "-")
                        row.add(rs.getString("land_forest_2026") ?: "-")

                        row.add(rs.getString("forest_cover_2016") ?: "-")
                        row.add(rs.getString("forest_cover_2018") ?: "-")
                        row.add(rs.getString("forest_cover_2020") ?: "-")
                        row.add(rs.getString("forest_cover_2022") ?: "-")
                        row.add(rs.getString("forest_cover_2024") ?: "-")
                        row.add(rs.getString("forest_cover_2026") ?: "-")

                        rows.add(row)
                    }
                }
            }

            val header = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("រាជធានី/ខេត្ត"),
                HeaderItem("ផ្ទៃដីព្រៃឈើ 2016"),
                HeaderItem("ផ្ទៃដីព្រៃឈើ 2018"),
                HeaderItem("ផ្ទៃដីព្រៃឈើ 2020"),
                HeaderItem("ផ្ទៃដីព្រៃឈើ 2022"),
                HeaderItem("ផ្ទៃដីព្រៃឈើ 2024"),
                HeaderItem("ផ្ទៃដីព្រៃឈើ 2026"),
                HeaderItem("ផ្ទៃដីគម្របព្រៃឈើ 2016"),
                HeaderItem("ផ្ទៃដីគម្របព្រៃឈើ 2018"),
                HeaderItem("ផ្ទៃដីគម្របព្រៃឈើ 2020"),
                HeaderItem("ផ្ទៃដីគម្របព្រៃឈើ 2022"),
                HeaderItem("ផ្ទៃដីគម្របព្រៃឈើ 2024"),
                HeaderItem("ផ្ទៃដីគម្របព្រៃឈើ 2026")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងបរិស្ថាន",
                sub_title = "19A2 ស្ថិតិផ្ទៃដីព្រៃឈើ និងផ្ទៃដីគម្របព្រៃឈើតាមរាជធានី ខេត្ត",
                tb_headers = listOf(header),
                tb_rows = rows
            )
        }
    }
}