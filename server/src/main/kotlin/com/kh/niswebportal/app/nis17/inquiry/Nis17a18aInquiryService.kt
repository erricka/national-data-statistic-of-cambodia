package com.kh.niswebportal.app.nis17.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis17a18aInquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_17a18_a"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
            val sql = """
                SELECT 
                    indicator_code,
                    indicator_kh,
                    unit,
                    year,
                    metric_value
                FROM nis_17a18_a_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY indicator_code ASC
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
                        row.add(rs.getString("indicator_kh"))
                        row.add(rs.getString("unit") ?: "")
                        row.add(rs.getString("year"))
                        row.add(rs.getString("metric_value") ?: "0")
                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("កូដ"),
                HeaderItem("សូចនាករ"),
                HeaderItem("ឯកតា"),
                HeaderItem("ឆ្នាំ"),
                HeaderItem("ចំនួន")
            )

            return DynamicReportResponse(
                title = "Table Data 17A18_a",
                sub_title = "17A18_a Data",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }
}
