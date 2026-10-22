package com.kh.niswebportal.app.nis07.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis07A5InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_07a5"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    crime_code, crime_name_kh, case_gen, pers_gen, case_actual, pers_actual,
                    in_case_investigate, in_pers_investigate, prior_case_investigate, prior_pers_investigate,
                    verdict_curnt_year, verdict_curnt_pers, eviden_gen, verdict_prev_year, verdict_prev_pers
                FROM nis_07a5_crime_suppression
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY crime_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()
                        row.add(rs.getString("crime_code"))
                        row.add(rs.getString("crime_name_kh"))
                        row.add(rs.getNullableInt("case_gen") ?: 0)
                        row.add(rs.getNullableInt("pers_gen") ?: 0)
                        row.add(rs.getNullableInt("case_actual") ?: 0)
                        row.add(rs.getNullableInt("pers_actual") ?: 0)
                        row.add(rs.getNullableInt("in_case_investigate") ?: 0)
                        row.add(rs.getNullableInt("in_pers_investigate") ?: 0)
                        row.add(rs.getNullableInt("prior_case_investigate") ?: 0)
                        row.add(rs.getNullableInt("prior_pers_investigate") ?: 0)
                        row.add(rs.getNullableInt("verdict_curnt_year") ?: 0)
                        row.add(rs.getNullableInt("verdict_curnt_pers") ?: 0)
                        row.add(rs.getNullableInt("eviden_gen") ?: 0)
                        row.add(rs.getNullableInt("verdict_prev_year") ?: 0)
                        row.add(rs.getNullableInt("verdict_prev_pers") ?: 0)
                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"), HeaderItem("ប្រភេទបទល្មើស"),
                HeaderItem("បង្ក្រាបរួម"), HeaderItem(""),
                HeaderItem("បង្ក្រាបជាក់ស្តែង"), HeaderItem(""),
                HeaderItem("ក្នុងរយៈពេល"), HeaderItem(""),
                HeaderItem("មុនរយៈពេល"), HeaderItem(""),
                HeaderItem("សាលក្រមឆ្នាំនេះ"), HeaderItem(""),
                HeaderItem("វត្ថុតាង"),
                HeaderItem("សាលក្រមឆ្នាំមុន"), HeaderItem("")
            )

            val subHeader = listOf(
                HeaderItem(""), HeaderItem(""),
                HeaderItem("ករណី"), HeaderItem("មនុស្ស"),
                HeaderItem("ករណី"), HeaderItem("មនុស្ស"),
                HeaderItem("ករណី"), HeaderItem("មនុស្ស"),
                HeaderItem("ករណី"), HeaderItem("មនុស្ស"),
                HeaderItem("ករណី"), HeaderItem("មនុស្ស"),
                HeaderItem(""),
                HeaderItem("ករណី"), HeaderItem("មនុស្ស")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងមហាផ្ទៃ",
                sub_title = "07A5 ស្ថិតិលទ្ធផលបង្ក្រាបបទល្មើសព្រហ្មទណ្ឌប្រចាំឆ្នាំ",
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