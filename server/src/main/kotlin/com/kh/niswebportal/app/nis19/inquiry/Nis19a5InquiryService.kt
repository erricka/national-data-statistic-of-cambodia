package com.kh.niswebportal.app.nis19.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis19a5InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_19a5"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val expSql = """
                SELECT chapter_code, sub_account_code, account_code, description, 
                       financial_law_2022, credit_movement_increase, actual_implementation, ratio_percentage
                FROM nis_19a5_ministry_expenditures
""".trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(expSql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var idx = 1
                    while (rs.next()) {
                        rows.add(listOf(
                            idx++,
                            rs.getString("chapter_code") ?: "",
                            rs.getString("sub_account_code") ?: "",
                            rs.getString("account_code") ?: "",
                            rs.getString("description"),
                            rs.getString("financial_law_2022") ?: "-",
                            rs.getString("credit_movement_increase") ?: "-",
                            rs.getString("actual_implementation") ?: "-",
                            rs.getString("ratio_percentage") ?: "-"
                        ))
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ជំពូក"),
                HeaderItem("គណនី"),
                HeaderItem("អនុគណនី"),
                HeaderItem("បរិយាយ"),
                HeaderItem("ច្បាប់ហិរញ្ញវត្ថុ ឆ្នាំ ២០២២"),
                HeaderItem("ចលនាឥណទាន (កើន)"),
                HeaderItem("អនុវត្តជាក់ស្តែង"),
                HeaderItem("សមាមាត្រ%")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងបរិស្ថាន",
                sub_title = "19A5 តារាងស្ថិតិស្តីពី ចំណាយរបស់ក្រសួងបរិស្ថាន",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}