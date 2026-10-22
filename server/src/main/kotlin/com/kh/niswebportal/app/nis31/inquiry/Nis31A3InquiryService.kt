package com.kh.niswebportal.app.nis31.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis31A3InquiryService(
    private val dataSource: DataSource
):NisInquiryStrategy {

    override val sourceId: String = "nis_31a3"

    override fun getDynamicReport(uploadId: String? ): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    year,
                    category_a_male,
                    category_a_female,
                    category_b_male,
                    category_b_female,
                    category_c_male,
                    category_c_female
                FROM nis_31a3_civil_servant_training_national
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY year ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(rs.getInt("year").toString())
                        row.add(rs.getNullableInt("category_a_male") ?: 0)
                        row.add(rs.getNullableInt("category_a_female") ?: 0)
                        row.add(rs.getNullableInt("category_b_male") ?: 0)
                        row.add(rs.getNullableInt("category_b_female") ?: 0)
                        row.add(rs.getNullableInt("category_c_male") ?: 0)
                        row.add(rs.getNullableInt("category_c_female") ?: 0)

                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ឆ្នាំ"),
                HeaderItem("ប្រភេទ(ក)"),
                HeaderItem(""),
                HeaderItem("ប្រភេទ(ខ)"),
                HeaderItem(""),
                HeaderItem("ប្រភេទ(គ)"),
                HeaderItem("")
            )

            val subHeader = listOf(
                HeaderItem(""),
                HeaderItem("ប្រុស"),
                HeaderItem("ស្រី"),
                HeaderItem("ប្រុស"),
                HeaderItem("ស្រី"),
                HeaderItem("ប្រុស"),
                HeaderItem("ស្រី")
            )

            return DynamicReportResponse(
                title = "31A ស្ថិតិមន្រ្តីរាជការ ប្រចាំឆ្នាំ",
                sub_title = "31A3 ស្ថិតិបណ្តុះបណ្តាលមន្រ្តីរាជការ ថ្នាក់ជាតិតាមប្រភេទ",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = rows
            )
        }
    }

    private fun ResultSet.getNullableInt(columnName: String): Int? {
        val value = this.getInt(columnName)
        return if (this.wasNull()) null else value
    }
}