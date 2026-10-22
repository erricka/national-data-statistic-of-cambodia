package com.kh.niswebportal.app.nis25.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis25A6InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_25a6"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    year,
                    trade_type_kh,
                    container_status_code,
                    container_status_name_kh,
                    metric_value_thousand_teus
                FROM nis_25a6_phnom_penh_port_container_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY year ASC, trade_type_kh ASC, container_status_code ASC
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
                        row.add(rs.getString("trade_type_kh"))
                        row.add(rs.getString("container_status_code"))
                        row.add(rs.getString("container_status_name_kh"))
                        row.add(rs.getBigDecimal("metric_value_thousand_teus") ?: "-")

                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ឆ្នាំ"),
                HeaderItem("ប្រភេទជួញដូរ"),
                HeaderItem("កូដ"),
                HeaderItem("ស្ថានភាពកុងតឺន័រ"),
                HeaderItem("បរិមាណ (ពាន់ TEUs)")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងសាធារណការ និងដឹកជញ្ជូន",
                sub_title = "25A6 បរិមាណកុងតឺន័រឆ្លងកាត់កំពង់ផែស្វយ័តភ្នំពេញ",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }
}