package com.kh.niswebportal.app.nis18.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis18a1InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_18a1"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    year,
                    total_count,
                    male_count,
                    female_count
                FROM nis_18a1_culture_civil_servants_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY year ASC
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
                        row.add(rs.getString("total_count") ?: "-")
                        row.add(rs.getString("male_count") ?: "-")
                        row.add(rs.getString("female_count") ?: "-")

                        rows.add(row)
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ឆ្នាំ"),
                HeaderItem("សរុប"),
                HeaderItem("ប្រុស"),
                HeaderItem("ស្រី")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងវប្បធម៌វិចិត្រសិល្បៈ",
                sub_title = "18A1 ស្ថិតិមន្រ្តីរាជការផ្នែកសិល្បៈវប្បធម៌",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}