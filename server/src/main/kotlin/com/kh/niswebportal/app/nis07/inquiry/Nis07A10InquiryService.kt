package com.kh.niswebportal.app.nis07.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis07A10InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_07a10"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    pro_code, provinces_name, num_khan, num_krong, num_district, total_district_level,
                    num_sangkat, num_commune, total_commune_level, old_villages, new_villages,
                    deleted_villages, grand_total_villages
                FROM nis_07a10_administrative_divisions
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id,pro_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()
                        row.add(rs.getString("pro_code"))
                        row.add(rs.getString("provinces_name"))
                        row.add(rs.getNullableInt("num_khan") ?: 0)
                        row.add(rs.getNullableInt("num_krong") ?: 0)
                        row.add(rs.getNullableInt("num_district") ?: 0)
                        row.add(rs.getNullableInt("total_district_level") ?: 0)
                        row.add(rs.getNullableInt("num_sangkat") ?: 0)
                        row.add(rs.getNullableInt("num_commune") ?: 0)
                        row.add(rs.getNullableInt("total_commune_level") ?: 0)
                        row.add(rs.getNullableInt("old_villages") ?: 0)
                        row.add(rs.getNullableInt("new_villages") ?: 0)
                        row.add(rs.getNullableInt("deleted_villages") ?: 0)
                        row.add(rs.getNullableInt("grand_total_villages") ?: 0)
                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"), HeaderItem("រាជធានី ខេត្ត"),
                HeaderItem("ស្ថិតិក្រុង ស្រុក ខណ្ឌ"), HeaderItem(""), HeaderItem(""), HeaderItem(""),
                HeaderItem("ស្ថិតិឃុំ សង្កាត់"), HeaderItem(""), HeaderItem(""),
                HeaderItem("ស្ថិតិភូមិ"), HeaderItem(""), HeaderItem(""), HeaderItem("")
            )

            val subHeader = listOf(
                HeaderItem(""), HeaderItem(""),
                HeaderItem("ខណ្ឌ"), HeaderItem("ក្រុង"), HeaderItem("ស្រុក"), HeaderItem("សរុប"),
                HeaderItem("សង្កាត់"), HeaderItem("ឃុំ"), HeaderItem("សរុប"),
                HeaderItem("ភូមិចាស់"), HeaderItem("បង្កើតថ្មី"), HeaderItem("លុប"), HeaderItem("សរុប")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងមហាផ្ទៃ",
                sub_title = "07A10 ស្ថិតិរដ្ឋបាលរាជធានី ខេត្ត ក្រុង ស្រុក ខណ្ឌ ឃុំ សង្កាត់ និងភូមិ គិតត្រឹមដំណាច់ឆ្នាំប្រចាំឆ្នាំ",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = rows
            )
        }
    }

    private fun ResultSet.getNullableInt(col: String): Int? {
        val v = this.getInt(col)
        return if (this.wasNull()) null else v
    }
}