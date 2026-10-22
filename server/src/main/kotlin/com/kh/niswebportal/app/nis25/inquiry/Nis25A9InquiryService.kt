package com.kh.niswebportal.app.nis25.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis25A9InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_25a9"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    year,
                    container_type_code,
                    flow_type_name_kh,
                    container_type_name_kh,
                    container_volume_thousand_teus
                FROM nis_25a9_sihanoukville_port_container_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY year ASC, container_type_code ASC
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
                        row.add(rs.getString("container_type_code"))
                        row.add(rs.getString("flow_type_name_kh"))
                        row.add(rs.getString("container_type_name_kh"))
                        row.add(rs.getObject("container_volume_thousand_teus") ?: "-")

                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ឆ្នាំ"),
                HeaderItem("កូដប្រភេទកុងតឺន័រ"),
                HeaderItem("ទិសដៅចរាចរណ៍"),
                HeaderItem("ប្រភេទកុងតឺន័រ"),
                HeaderItem("បរិមាណ (ពាន់TEUs)")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងសាធារណការ និងដឹកជញ្ជូន",
                sub_title = "25A9 បរិមាណកុងតឺន័រឆ្លងកាត់កំពង់ផែស្វយ័តក្រុងព្រះសីហនុ (ពាន់TEUs)",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }
}