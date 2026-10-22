package com.kh.niswebportal.app.nis11.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis11A1InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_11a1"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
// Sorting by id ASC guarantees we preserve the exact top-to-bottom layout of the Excel file
            // (e.g., 2025 block first, then 2024, etc.)
            val sql = """
                SELECT 
                    year,
                    category,
                    serial_no,
                    media_type,
                    pp_count,
                    relay_count,
                    total_count,
                    remarks
                FROM nis_11a1_media_statistics
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var lastYear = 0
                    var lastCategory = ""

                    while (rs.next()) {
                        val year = rs.getInt("year")
                        val category = rs.getString("category") ?: ""
                        val serial = rs.getString("serial_no") ?: ""
                        val mediaType = rs.getString("media_type") ?: ""
                        val ppCount = rs.getString("pp_count") ?: ""
                        val relayCount = rs.getString("relay_count") ?: ""
                        val totalCount = rs.getString("total_count") ?: ""
                        val remarks = rs.getString("remarks") ?: ""

                        // 1. Inject Year Grouping Row (Visually separates the annual blocks)
                        if (year != lastYear && year > 0) {
                            val yearText = "ស្ថិតិប្រព័ន្ធផ្សព្វផ្សាយរបស់ក្រសួងព័ត៌មាន ឆ្នាំ$year"
                            rows.add(listOf(yearText, "", "", "", "", ""))
                            lastYear = year
                            lastCategory = "" // Force category to reprint for the new year
                        }

                        // 2. Inject Category Grouping Row (e.g., "I. ស្ថានីយទូរទស្សន៍")
                        if (category != lastCategory && category.isNotBlank()) {
                            rows.add(listOf(category, "", "", "", "", ""))
                            lastCategory = category
                        }

                        // 3. Inject Actual Data Row (6 Columns)
                        rows.add(listOf(serial, mediaType, ppCount, relayCount, totalCount, remarks))
                    }
                }
            }

            // Flat, single-tier headers mapping perfectly to the 6 columns
            val headerRow = listOf(
                HeaderItem(title = "ល.រ"),
                HeaderItem(title = "ឈ្មោះ"),
                HeaderItem(title = "រាជធានីភ្នំពេញ"),
                HeaderItem(title = "ស្ថានីយ៍ផ្សាយបន្ត"),
                HeaderItem(title = "សរុប"),
                HeaderItem(title = "ផ្សេងៗ")
            )

            return DynamicReportResponse(
                title = "ក្រសួងព័ត៌មាន", // Ministry of Information
                sub_title = "11A1 ស្ថិតិប្រព័ន្ធផ្សព្វផ្សាយ", // Media Statistics
                tb_headers = listOf(headerRow),
                tb_rows = rows
            )
        }
    }
}