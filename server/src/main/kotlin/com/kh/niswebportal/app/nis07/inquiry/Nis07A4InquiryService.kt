package com.kh.niswebportal.app.nis07.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis07A4InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_07a4"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    offense_code,
                    offense_name,
                    year_prev,
                    year_curr,
                    change_count,
                    change_percent,
                    total_damage
                FROM nis_07a4_criminal_offenses
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(rs.getString("offense_code"))
                        row.add(rs.getString("offense_name"))
                        row.add(rs.getNullableInt("year_prev") ?: 0)
                        row.add(rs.getNullableInt("year_curr") ?: 0)
                        row.add(rs.getNullableInt("change_count") ?: 0)
                        row.add(rs.getNullableDouble("change_percent") ?: 0.0)
                        row.add(rs.getString("total_damage") ?: "")

                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ប្រភេទបទល្មើស"),
                HeaderItem("ឆ្នាំ២០២៤"),
                HeaderItem("ឆ្នាំ២០២៥"),
                HeaderItem("កើន/ថយ"),
                HeaderItem(""),
                HeaderItem("ការខូចខាតរួម")
            )

            val subHeader = listOf(
                HeaderItem(""),
                HeaderItem(""),
                HeaderItem(""),
                HeaderItem(""),
                HeaderItem("ចំនួន"),
                HeaderItem("ភាគរយ"),
                HeaderItem("")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងមហាផ្ទៃ",
                sub_title = "07A4 ស្ថិតិបទល្មើសព្រហ្មទណ្ឌប្រចាំឆ្នាំ",
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