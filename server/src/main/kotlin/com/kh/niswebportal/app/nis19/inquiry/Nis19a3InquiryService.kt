package com.kh.niswebportal.app.nis19.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis19a3InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_19a3"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    category_code,
                    management_category,
                    number_of_areas,
                    ecosystem_code,
                    main_ecosystem_coverage,
                    area_in_hectares
                FROM nis_19a3_ecosystems_protected_areas
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY category_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(index++)
                        row.add(rs.getString("category_code"))
                        row.add(rs.getString("management_category"))
                        row.add(rs.getString("number_of_areas") ?: "-")
                        row.add(rs.getString("ecosystem_code") ?: "-")
                        row.add(rs.getString("main_ecosystem_coverage") ?: "-")
                        row.add(rs.getString("area_in_hectares") ?: "-")

                        rows.add(row)
                    }
                }
            }

            val header = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("កូដ"),
                HeaderItem("Management Category"),
                HeaderItem("Number"),
                HeaderItem("Ecosystem Code"),
                HeaderItem("Main Ecosystem Coverage"),
                HeaderItem("Area (in hectares)")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងបរិស្ថាន",
                sub_title = "19A3 ប្រពន្ធ័អេកូទ្បូស៊ីសំខាន់ៗ ក្នុងប្រពន្ធ័តំបន់ការពារថ្នាក់ជាតិបច្ចុប្បន្ន (Major ecosystems covered under the current national protected areas system)",
                tb_headers = listOf(header),
                tb_rows = rows
            )
        }
    }
}