package com.kh.niswebportal.app.nis25.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis25A8InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_25a8"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    year,
                    cargo_type_code,
                    flow_type_name_kh,
                    cargo_type_name_kh,
                    cargo_volume_thousand_tons
                FROM nis_25a8_sihanoukville_port_cargo_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY year ASC, cargo_type_code ASC
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
                        row.add(rs.getString("cargo_type_code"))
                        row.add(rs.getString("flow_type_name_kh"))
                        row.add(rs.getString("cargo_type_name_kh"))
                        row.add(rs.getObject("cargo_volume_thousand_tons") ?: "-")

                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ឆ្នាំ"),
                HeaderItem("កូដប្រភេទទំនិញ"),
                HeaderItem("ទិសដៅចរាចរណ៍"),
                HeaderItem("ប្រភេទទំនិញ"),
                HeaderItem("បរិមាណ (ពាន់តោន)")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងសាធារណការ និងដឹកជញ្ជូន",
                sub_title = "25A8 បរិមាណទំនិញឆ្លងកាត់កំពង់ផែស្វយ័តក្រុងព្រះសីហនុ (ពាន់តោន)",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }
}