package com.kh.niswebportal.app.nis25.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis25A2InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_25a2"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    indicator_code,
                    indicator_name_kh,
                    road_type_code,
                    road_type_name_kh,
                    year,
                    metric_value
                FROM nis_25a2_national_provincial_roads_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY indicator_code ASC, year ASC, road_type_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(index++)
                        row.add(rs.getString("indicator_code"))
                        row.add(rs.getString("indicator_name_kh"))
                        row.add(rs.getString("road_type_name_kh"))
                        row.add(rs.getString("year"))
                        row.add(rs.getBigDecimal("metric_value") ?: "-")

                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("កូដ"),
                HeaderItem("សូចនាករ"),
                HeaderItem("ប្រភេទផ្លូវ/ស្ពាន"),
                HeaderItem("ឆ្នាំ"),
                HeaderItem("តម្លៃ")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងសាធារណការ និងដឹកជញ្ជូន",
                sub_title = "25A2 ផ្លូវជាតិ និងផ្លូវខេត្ត តាមប្រភេទផ្លូវ",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }
}