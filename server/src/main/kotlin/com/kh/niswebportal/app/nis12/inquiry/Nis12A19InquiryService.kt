package com.kh.niswebportal.app.nis12.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis12A19InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_12a19"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    level_code,
                    level_no,
                    level_name_kh,
                    year,
                    physicians_count,
                    docs_nurses_count,
                    other_staff_count,
                    patients_count,
                    beds_count,
                    others_count
                FROM nis_12a19_health_staff_by_facility
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val row = listOf<Any?>(
                            rs.getString("level_code"),
                            rs.getString("level_no"),
                            rs.getString("level_name_kh"),
                            rs.getInt("year"),
                            rs.getNullableDouble("physicians_count"),
                            rs.getNullableDouble("docs_nurses_count"),
                            rs.getNullableDouble("other_staff_count"),
                            rs.getNullableDouble("patients_count"),
                            rs.getNullableDouble("beds_count"),
                            rs.getNullableDouble("others_count")
                        )
                        rows.add(row)
                    }
                }
            }

            val headers = listOf(
                HeaderItem("កូដ (Code)"),
                HeaderItem("លរ"),
                HeaderItem("កម្រិត (Facility Level)"),
                HeaderItem("ឆ្នាំ"),
                HeaderItem("ចំនួនវេជ្ជបណ្ឌិត"),
                HeaderItem("ចំនួនគ្រូពេទ្យ និងគិលានុប្បដ្ឋាក"),
                HeaderItem("បុគ្គលិកដទៃទៀត"),
                HeaderItem("ចំនួនអ្នកជំងឺ"),
                HeaderItem("ចំនួនគ្រែ"),
                HeaderItem("ដទៃទៀត")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងសុខាភិបាល",
                sub_title = "12a19 ចំនួនឆ្មប គុលានុដ្ឋាន គ្រូពេទ្យ វេជ្ជបណ្ឌិត បុគ្គលិកសុខាភិបាល តាមមូលដ្ឋានសុខាភិបាល",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }

    
}