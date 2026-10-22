package com.kh.niswebportal.app.nis08.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis08A2InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_08a2"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    level_type,
                    province_name,
                    serial_no,
                    description,
                    complaints_count,
                    cases_count
                FROM nis_08a2_complaint_registry
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var lastLevel = ""
                    var lastProvince = ""

                    while (rs.next()) {
                        val level = rs.getString("level_type") ?: ""
                        val province = rs.getString("province_name") ?: ""
                        val serial = rs.getString("serial_no") ?: ""
                        val desc = rs.getString("description") ?: ""
                        val complaints = rs.getString("complaints_count") ?: ""
                        val cases = rs.getString("cases_count") ?: ""

                        // 1. Level Grouping Row
                        if (level != lastLevel && level.isNotBlank()) {
                            rows.add(listOf(level, "", "", ""))
                            lastLevel = level
                            lastProvince = ""
                        }

                        // 2. Province Grouping Row (Outputs in Column 2 to stagger the visual layout)
                        if (province != lastProvince && province.isNotBlank()) {
                            rows.add(listOf("", province, "", ""))
                            lastProvince = province
                        }

                        // 3. Actual Data Row (4 columns)
                        rows.add(listOf(serial, desc, complaints, cases))
                    }
                }
            }

            // 2-Tier Headers simulated WITHOUT colSpan or rowSpan
            val topHeader = listOf(
                HeaderItem(title = "\u200B"),   // Placeholder above ល.រ
                HeaderItem(title = "\u200B"),   // Placeholder above បរិយាយ
                HeaderItem(title = "ចំនួន"),      // "Count" header
                HeaderItem(title = "")          // Empty string to simulate the span over 'Cases'
            )

            val subHeader = listOf(
                HeaderItem(title = "ល.រ"),      // No.
                HeaderItem(title = "បរិយាយ"),    // Description
                HeaderItem(title = "បណ្តឹង"),     // Complaints
                HeaderItem(title = "ករណី")       // Cases
            )

            return DynamicReportResponse(
                title = "ក្រសួងអធិការកិច្ច",
                sub_title = "08A2 ស្ថិតិពាក្យបណ្តឹង",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = rows
            )
        }
    }
}