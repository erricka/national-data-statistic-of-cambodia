package com.kh.niswebportal.app.nis25.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis25A12InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_25a12"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    year,
                    indicator_code,
                    mode_port_name_kh,
                    route_type_name_kh,
                    passenger_count
                FROM nis_25a12_passenger_transit_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY year ASC, indicator_code ASC
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
                        row.add(rs.getString("indicator_code"))
                        row.add(rs.getString("mode_port_name_kh"))
                        row.add(rs.getString("route_type_name_kh"))
                        row.add(rs.getObject("passenger_count") ?: "-")

                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ឆ្នាំ"),
                HeaderItem("កូដសូចនាករ"),
                HeaderItem("កំពង់ផែ / ផ្លូវដឹកជញ្ជូន"),
                HeaderItem("ខ្សែផ្លូវ / ប្រភេទ"),
                HeaderItem("ចំនួនអ្នកដំណើរ (នាក់)")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងសាធារណការ និងដឹកជញ្ជូន",
                sub_title = "25A12 ចំនួនអ្នកដំណើរឆ្លងកាត់តាមកំពង់ផែស្វយ័តភ្នំពេញ កំពង់ផែស្វយ័តក្រុងព្រះសីហនុ និងផ្លូវដែក (នាក់)",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }
}