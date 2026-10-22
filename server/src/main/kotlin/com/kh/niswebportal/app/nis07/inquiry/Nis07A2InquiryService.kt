package com.kh.niswebportal.app.nis07.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis07A2InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_07a2"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    province_code,
                    province_name,
                    plan_to_provide,
                    total_data_received,
                    completed_data,
                    broken_data,
                    duplicated_data,
                    print_data,
                    offer_data,
                    idcard_print,
                    idcard_offer
                FROM nis_07a2_national_id_card
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id ,province_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(rs.getString("province_code"))
                        row.add(rs.getString("province_name"))
                        row.add(rs.getNullableInt("plan_to_provide") ?: 0)
                        row.add(rs.getNullableInt("total_data_received") ?: 0)
                        row.add(rs.getNullableInt("completed_data") ?: 0)
                        row.add(rs.getNullableInt("broken_data") ?: 0)
                        row.add(rs.getNullableInt("duplicated_data") ?: 0)
                        row.add(rs.getNullableInt("print_data") ?: 0)
                        row.add(rs.getNullableInt("offer_data") ?: 0)
                        row.add(rs.getNullableInt("idcard_print") ?: 0)
                        row.add(rs.getNullableInt("idcard_offer") ?: 0)

                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("រាជធានី ខេត្ត"),
                HeaderItem("ផែនការត្រូវផ្តល់"),
                HeaderItem("ទិន្នន័យទទួលបាន"),
                HeaderItem(""),
                HeaderItem(""),
                HeaderItem(""),
                HeaderItem(""),
                HeaderItem(""),
                HeaderItem("ចំនួនអត្តសញ្ញាណបណ្ណ"),
                HeaderItem("")
            )

            val middleHeader = listOf(
                HeaderItem(""),
                HeaderItem(""),
                HeaderItem(""),
                HeaderItem("ចំនួនរួម"),
                HeaderItem("បានការ"),
                HeaderItem("មិនបានការ"),
                HeaderItem(""),
                HeaderItem("បោះពុម្ភ"),
                HeaderItem("ប្រគល់"),
                HeaderItem("បោះពុម្ភ"),
                HeaderItem("ប្រគល់")
            )

            val bottomHeader = listOf(
                HeaderItem(""),
                HeaderItem(""),
                HeaderItem(""),
                HeaderItem(""),
                HeaderItem(""),
                HeaderItem("ខូច"),
                HeaderItem("ស្ទួន"),
                HeaderItem(""),
                HeaderItem(""),
                HeaderItem(""),
                HeaderItem("")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងមហាផ្ទៃ",
                sub_title = "07A2 ស្ថិតិនៃការផ្តល់អត្តសញ្ញាណបណ្ណសញ្ជាតិខ្មែរប្រចាំឆ្នាំ",
                tb_headers = listOf(topHeader, middleHeader, bottomHeader),
                tb_rows = rows
            )
        }
    }

    private fun ResultSet.getNullableInt(columnName: String): Int? {
        val value = this.getInt(columnName)
        return if (this.wasNull()) null else value
    }
}