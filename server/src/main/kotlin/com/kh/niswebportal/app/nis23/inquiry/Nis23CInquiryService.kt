package com.kh.niswebportal.app.nis23.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis23CInquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_23c"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    indicator_code,
                    description_kh,
                    category,
                    year
                FROM nis_23c_challenges_requests
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
                        row.add(rs.getString("description_kh"))
                        row.add(if (rs.getString("category") == "CHALLENGE") "បញ្ហាប្រឈម" else "សំណូមពរ")
                        row.add(rs.getString("year"))

                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("កូដ"),
                HeaderItem("បរិយាយ (បញ្ហាប្រឈម និង សំណូមពរ)"),
                HeaderItem("ប្រភេទ"),
                HeaderItem("ឆ្នាំ")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាលក្រសួងធម្មការ និង សាសនា",
                sub_title = "23C បញ្ហាប្រឈម និង សំណូមពរ",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }
}