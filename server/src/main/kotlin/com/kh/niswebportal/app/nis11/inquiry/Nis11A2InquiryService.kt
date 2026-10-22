package com.kh.niswebportal.app.nis11.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis11A2InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_11a2"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
// Order by Year DESC so the newest year displays at the top of the UI
            val sql = """
                SELECT 
                    year,
                    serial_no,
                    company_name,
                    company_type,
                    annual_income,
                    annual_expense,
                    phone
                FROM nis_11a2_tv_radio_companies
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY year DESC, id ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var lastYear = 0

                    while (rs.next()) {
                        val year = rs.getInt("year")
                        val serial = rs.getString("serial_no") ?: ""
                        val name = rs.getString("company_name") ?: ""
                        val type = rs.getString("company_type") ?: ""
                        val income = rs.getNullableDouble("annual_income") ?: ""
                        val expense = rs.getNullableDouble("annual_expense") ?: ""
                        val phone = rs.getString("phone") ?: ""

                        // Automatically inject a row separating the reporting years
                        if (year != lastYear && year > 0) {
                            val yearText = "ស្ថិតិក្រុមហ៊ុនទូរទស្សន៍ និងវិទ្យុ ឆ្នាំ$year"
                            rows.add(listOf(yearText, "", "", "", "", ""))
                            lastYear = year
                        }

                        rows.add(listOf(serial, name, type, income, expense, phone))
                    }
                }
            }

            val headerRow = listOf(
                HeaderItem(title = "ល.រ"),
                HeaderItem(title = "អង្គភាព ក្រុមហ៊ុន"),
                HeaderItem(title = "ប្រភេទក្រុមហ៊ុន"),
                HeaderItem(title = "ចំណូលប្រចាំឆ្នាំ"),
                HeaderItem(title = "ចំណាយប្រចាំឆ្នាំ"),
                HeaderItem(title = "ទូរស័ព្ទ")
            )

            return DynamicReportResponse(
                title = "ក្រសួងព័ត៌មាន", // Ministry of Information
                sub_title = "11A2 ស្ថិតិក្រុមហ៊ុនទូរទស្សន៍ និងវិទ្យុ", // TV & Radio Company Statistics
                tb_headers = listOf(headerRow),
                tb_rows = rows
            )
        }
    }

    
}