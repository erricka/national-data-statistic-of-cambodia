package com.kh.niswebportal.app.nis61.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis61t26InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_61t26"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT category_group, expense_type, sample_size, numeric_value, unit,
                       percentage_of_monthly_wage, family_members_change, change_vs_previous_year_pct
                FROM nis_61t26_workers_living_conditions
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
                        val type = rs.getString("expense_type") ?: ""

                        rows.add(
                            listOf(
                                idx++,
                                "[$group] $type",
                                rs.getString("sample_size") ?: "-",
                                rs.getString("numeric_value") ?: "-",
                                rs.getString("unit") ?: "-",
                                rs.getString("percentage_of_monthly_wage") ?: "-",
                                rs.getString("family_members_change") ?: "-",
                                rs.getString("change_vs_previous_year_pct") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ប្រភេទចំណាយ / បន្ទុកគ្រួសារ (Expense Type / Family Burden)"),
                HeaderItem("ទំហំសំណាក (Sample Size N)"),
                HeaderItem("តម្លៃលេខ (Numeric Value)"),
                HeaderItem("ឯកតា (Unit)"),
                HeaderItem("ភាគរយនៃប្រាក់ឈ្នួលប្រចាំខែ (%) (% of Monthly Wage)"),
                HeaderItem("បម្រែបម្រួលធៀបនឹងឆ្នាំមុន (គិតជានាក់) (Family Change)"),
                HeaderItem("បម្រែបម្រួលធៀបនឹងឆ្នាំមុន (+/- %) (YoY Change %) ")
            )

            return DynamicReportResponse(
                title = "ស្ថិតិអង្កេតស្តីពីស្ថានភាពតម្រូវការរស់នៅរបស់កម្មករនិយោជិតប្រចាំឆ្នាំ (Annual Workers' Living Conditions and Expenditure Survey Statistics)",
                sub_title = "NIS 61T_26 - ស្ថិតិទីផ្សារការងារនិងកម្លាំងពលកម្ម",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}