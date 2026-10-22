package com.kh.niswebportal.app.nis28.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis28A4InquiryService(
    private val dataSource: DataSource
):NisInquiryStrategy {

    override val sourceId: String = "nis_28a4"

    @Transactional
    override fun getDynamicReport(uploadId: String? ): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    year,
                    province_code,
                    province_name_kh,
                    unit,
                    total_titles,
                    total_households,
                    total_hectares
                FROM nis_28a4_subsequent_land_registration
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
                        row.add(rs.getNullableDouble("total_titles") ?: 0.0)
                        row.add(rs.getNullableDouble("total_households") ?: 0.0)
                        row.add(rs.getNullableDouble("total_hectares") ?: 0.0)

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
                HeaderItem("ចំនួនបណ្ណ (គិតជាពាន់បណ្ណ)"),
                HeaderItem("ចំនួនគ្រួសារ (គិតជាពាន់គ្រួសារ)"),
                HeaderItem("ទំហំ (ហិកតា)")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ស្ថិតិវិស័យរៀបចំដែនដី នគរូបនីយកម្ម និងសំណង់",
                sub_title = "28A4 ការចុះបញ្ជីដីបន្ត",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }

    
}