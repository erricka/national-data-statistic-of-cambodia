package com.kh.niswebportal.app.nis26.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis26A7InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_26a7"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
// Grouping output by Year first, then Court Code
            val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY court_code) as court_order,
                    court_code,
                    institution_name,
                    year,
                    judge_count,
                    prosecutor_count,
                    secretariat_count,
                    clerk_count,
                    bailiff_count,
                    lawyer_count,
                    plaintiff_count,
                    total_count
                FROM nis_26a7_court_inspections
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY year ASC, court_order ASC
            """.trimIndent()

            val headerRow = listOf(
                HeaderItem(title = "កូដ"),
                HeaderItem(title = "ស្ថាប័ន"),
                HeaderItem(title = "ចៅក្រម"),
                HeaderItem(title = "អយ្យការ"),
                HeaderItem(title = "មន្រ្តីលេខាធិការដ្ឋាន"),
                HeaderItem(title = "ក្រឡាបញ្ជី"),
                HeaderItem(title = "អាជ្ញាសាលា"),
                HeaderItem(title = "មេធាវី"),
                HeaderItem(title = "ម្ចាស់បណ្ដឹង"),
                HeaderItem(title = "សរុប")
            )

            val tbRows = mutableListOf<List<Any?>>()
            var lastYear = -1

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val year = rs.getInt("year")

                        // Dynamically inject a Year Header Row whenever the year changes
                        if (year != lastYear && year > 0) {
                            tbRows.add(listOf(
                                "ប្រចាំឆ្នាំ $year", "", "", "", "", "", "", "", "", ""
                            ))
                            lastYear = year
                        }

                        tbRows.add(
                            listOf(
                                rs.getString("court_code"),
                                rs.getString("institution_name"),
                                rs.getString("judge_count") ?: "",
                                rs.getString("prosecutor_count") ?: "",
                                rs.getString("secretariat_count") ?: "",
                                rs.getString("clerk_count") ?: "",
                                rs.getString("bailiff_count") ?: "",
                                rs.getString("lawyer_count") ?: "",
                                rs.getString("plaintiff_count") ?: "",
                                rs.getString("total_count") ?: ""
                            )
                        )
                    }
                }
            }

            return DynamicReportResponse(
                title = "ក្រសួងយុត្តិធម៌", // Ministry of Justice
                sub_title = "26A7 ស្ថិតិកិច្ចការអធិការកិច្ចនៅតាមតុលាការគ្រប់ជាន់ថ្នាក់ប្រចាំឆ្នាំ", // Court Inspections Statistics
                tb_headers = listOf(headerRow),
                tb_rows = tbRows
            )
        }
    }
}