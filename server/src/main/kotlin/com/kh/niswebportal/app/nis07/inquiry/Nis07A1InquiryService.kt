package com.kh.niswebportal.app.nis07.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis07A1InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_07a1"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    province_code,
                    province_name,
                    num_households,
                    total_members,
                    female_members,
                    age_15_over_total,
                    age_15_over_female,
                    age_18_over_total,
                    age_18_over_female
                FROM nis_07a1_registered_population
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id ASC, province_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(rs.getString("province_code"))
                        row.add(rs.getString("province_name"))
                        row.add(rs.getNullableInt("num_households") ?: 0)
                        row.add(rs.getNullableInt("total_members") ?: 0)
                        row.add(rs.getNullableInt("female_members") ?: 0)
                        row.add(rs.getNullableInt("age_15_over_total") ?: 0)
                        row.add(rs.getNullableInt("age_15_over_female") ?: 0)
                        row.add(rs.getNullableInt("age_18_over_total") ?: 0)
                        row.add(rs.getNullableInt("age_18_over_female") ?: 0)

                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("រាជធានី ខេត្ត"),
                HeaderItem("ចំនួនគ្រួសារ"),
                HeaderItem("ចំនួនមនុស្សរួម"),
                HeaderItem(""),
                HeaderItem("អាយុ ១៥ ឆ្នាំឡើង"),
                HeaderItem(""),
                HeaderItem("អាយុ ១៨ ឆ្នាំឡើង"),
                HeaderItem("")
            )

            val subHeader = listOf(
                HeaderItem(""),
                HeaderItem(""),
                HeaderItem(""),
                HeaderItem("សរុប"),
                HeaderItem("ស្រី"),
                HeaderItem("សរុប"),
                HeaderItem("ស្រី"),
                HeaderItem("សរុប"),
                HeaderItem("ស្រី")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងមហាផ្ទៃ",
                sub_title = "07A1 ស្ថិតិប្រជាពលរដ្ឋដែលបានចុះបញ្ជីគិតត្រឹមដំណាច់ឆ្នាំប្រចាំឆ្នាំ",
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