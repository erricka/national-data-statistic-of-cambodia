package com.kh.niswebportal.app.nis08.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis08A3InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_08a3"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
// Sort by 'id ASC' to precisely preserve the top-to-bottom order of the original Excel file
            val sql = """
                SELECT 
                    level_type,
                    province_name,
                    serial_no,
                    description,
                    location
                FROM nis_08a3_monitoring_registry
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
                        val loc = rs.getString("location") ?: ""

                        // 1. Level Grouping Row (Outputs in Column 1)
                        if (level != lastLevel && level.isNotBlank()) {
                            rows.add(listOf(level, "", ""))
                            lastLevel = level
                            lastProvince = ""
                        }

                        // 2. Province Grouping Row (Outputs in Column 2 to match Excel staggering layout)
                        if (province != lastProvince && province.isNotBlank()) {
                            rows.add(listOf("", province, ""))
                            lastProvince = province
                        }

                        // 3. Actual Data Row (3 Columns)
                        rows.add(listOf(serial, desc, loc))
                    }
                }
            }

            // Headers define exactly 3 columns
            val headerRow = listOf(
                HeaderItem(title = "ល.រ"),        // No.
                HeaderItem(title = "បរិយាយ"),      // Description
                HeaderItem(title = "ទីកន្លែង")       // Location
            )

            return DynamicReportResponse(
                title = "ក្រសួងអធិការកិច្ច",
                sub_title = "08A3 ការតាមដានការអនុវត្តច្បាប់", // Monitoring of Law Enforcement
                tb_headers = listOf(headerRow),
                tb_rows = rows
            )
        }
    }
}