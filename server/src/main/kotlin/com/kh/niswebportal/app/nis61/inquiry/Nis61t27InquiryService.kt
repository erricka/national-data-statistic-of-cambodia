package com.kh.niswebportal.app.nis61.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis61t27InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_61t27"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT category_group, breakdown_item, surveyed_count, average_monthly_base_wage,
                       allowances_or_overtime, estimated_labor_productivity_index
                FROM nis_61t27_wages_productivity_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(dataSql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var idx = 1
                    while (rs.next()) {
                        val group = rs.getString("category_group") ?: ""
                        val item = rs.getString("breakdown_item") ?: ""

                        rows.add(
                            listOf(
                                idx++,
                                "[$group] $item",
                                rs.getString("surveyed_count") ?: "-",
                                rs.getString("average_monthly_base_wage") ?: "-",
                                rs.getString("allowances_or_overtime") ?: "-",
                                rs.getString("estimated_labor_productivity_index") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ប្រភេទនៃការបែងចែក / វិស័យ (Breakdown / Category)"),
                HeaderItem("ចំនួនសហគ្រាស / កម្មករដែលបានអង្កេត (Surveyed Count)"),
                HeaderItem("ប្រាក់ឈ្នួលមូលដ្ឋានមធ្យមប្រចាំខែ (Average Monthly Base Wage)"),
                HeaderItem("ប្រាក់អត្ថប្រយោជន៍ផ្សេងៗ / ម៉ោងបន្ថែម (Allowances / Overtime)"),
                HeaderItem("សន្ទស្សន៍ផលិតភាពការងារប៉ាន់ស្មាន (Estimated Productivity Index)")
            )

            return DynamicReportResponse(
                title = "ស្ថិតិអង្កេតប្រាក់ឈ្នួល និងផលិតភាពការងារប្រចាំឆ្នាំ (Annual Wages and Labor Productivity Survey Statistics)",
                sub_title = "NIS 61T_27 - ស្ថិតិទីផ្សារការងារនិងកម្លាំងពលកម្ម",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}