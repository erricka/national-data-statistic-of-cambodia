package com.kh.niswebportal.app.nis08.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis08A4InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_08a4"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
// Order by ID ASC preserves the exact chronological top-to-bottom entry of the Excel file
            val sql = """
                SELECT 
                    level_type,
                    group_name,
                    serial_no,
                    activity_date,
                    location_1,
                    location_2,
                    topic_desc,
                    metric_total,
                    metric_female
                FROM nis_08a4_dissemination_registry
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var lastLevel = ""
                    var lastGroup = ""

                    while (rs.next()) {
                        val level = rs.getString("level_type") ?: ""
                        val group = rs.getString("group_name") ?: ""

                        val serial = rs.getString("serial_no") ?: ""
                        val date = rs.getString("activity_date") ?: ""
                        val loc1 = rs.getString("location_1") ?: ""
                        val loc2 = rs.getString("location_2") ?: ""
                        val topic = rs.getString("topic_desc") ?: ""
                        val mTotal = rs.getString("metric_total") ?: ""
                        val mFemale = rs.getString("metric_female") ?: ""

                        // 1. Insert Level Grouping Row (Outputs natively in Col 1)
                        if (level != lastLevel && level.isNotBlank()) {
                            rows.add(listOf(level, "", "", "", "", "", ""))
                            lastLevel = level
                            lastGroup = ""
                        }

                        // 2. Insert Category/Province Grouping Row
                        if (group != lastGroup && group.isNotBlank()) {
                            rows.add(listOf(group, "", "", "", "", "", ""))
                            lastGroup = group
                        }

                        // 3. Insert Actual Data Row (7 columns)
                        rows.add(listOf(serial, date, loc1, loc2, topic, mTotal, mFemale))
                    }
                }
            }

            // Construct 2-Tier Headers using \u200B to safely merge columns in the frontend
            val topHeader = listOf(
                HeaderItem(title = "\u200B"),
                HeaderItem(title = "\u200B"),
                HeaderItem(title = "\u200B"),
                HeaderItem(title = "\u200B"),
                HeaderItem(title = "\u200B"),
                HeaderItem(title = "ចំនួនអ្នកចូលរួម"), // Participants/Metrics
                HeaderItem(title = "")
            )

            val subHeader = listOf(
                HeaderItem(title = "ល.រ"),
                HeaderItem(title = "កាលបរិច្ឆេទ"),
                HeaderItem(title = "ស្រុក/ក្រុង/ទីកន្លែង"),
                HeaderItem(title = "ខេត្ត/ទីតាំង"),
                HeaderItem(title = "ប្រធានបទ/ឈ្មោះច្បាប់"),
                HeaderItem(title = "សរុប"),
                HeaderItem(title = "ស្រី")
            )

            return DynamicReportResponse(
                title = "ក្រសួងអធិការកិច្ច",
                sub_title = "08A4 ការងារផ្សព្វផ្សាយអប់រំច្បាប់", // Law Dissemination & Education
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = rows
            )
        }
    }
}