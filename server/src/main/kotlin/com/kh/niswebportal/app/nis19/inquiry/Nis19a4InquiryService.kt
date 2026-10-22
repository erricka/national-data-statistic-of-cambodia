package com.kh.niswebportal.app.nis19.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis19a4InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_19a4"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    province_code,
                    province_name,
                    environmental_offenses,
                    natural_resource_offenses
                FROM nis_19a4_environmental_offenses_stats
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
                        row.add(rs.getString("environmental_offenses") ?: "-")
                        row.add(rs.getString("natural_resource_offenses") ?: "-")

                        rows.add(row)
                    }
                }
            }

            val header = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("រាជធានី/ខេត្ត"),
                HeaderItem("បទល្មើសបរិស្ថាន (ករណី)"),
                HeaderItem("បទល្មើសធនធានធម្មជាតិ (ករណី)")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងបរិស្ថាន",
                sub_title = "19A4 ស្ថិតិបទល្មើសបរិស្ថាន និងធនធានធម្មជាតិ",
                tb_headers = listOf(header),
                tb_rows = rows
            )
        }
    }
}