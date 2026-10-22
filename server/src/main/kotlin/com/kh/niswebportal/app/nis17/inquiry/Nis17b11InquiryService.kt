package com.kh.niswebportal.app.nis17.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis17b11InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_17b11"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
// 1. Fetch dynamic list of distinct years
            val years = mutableListOf<String>()
            val yearSql = """
                SELECT DISTINCT year 
                FROM nis_17b11_agricultural_community_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY year ASC
            """.trimIndent()

            conn.prepareStatement(yearSql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        years.add(rs.getString("year"))
                    }
                }
            }

            // 2. Aggregate pivoted dynamic year columns
            val yearSelects = years.joinToString(",\n") { y ->
                "MAX(CASE WHEN year = '$y' THEN metric_value END) AS year_$y"
            }

            val sql = """
                SELECT 
                    indicator_code,
                    indicator_kh,
                    unit
                    ${if (years.isNotEmpty()) ", $yearSelects" else ""}
                FROM nis_17b11_agricultural_community_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                GROUP BY indicator_code, indicator_kh, unit
                ORDER BY CAST(REGEXP_REPLACE(indicator_code, '[^0-9]', '', 'g') AS INTEGER) ASC, indicator_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(index++)
                        row.add(rs.getString("indicator_code"))
                        row.add(rs.getString("indicator_kh"))
                        row.add(rs.getString("unit") ?: "-")

                        for (y in years) {
                            row.add(rs.getString("year_$y") ?: "-")
                        }

                        rows.add(row)
                    }
                }
            }

            // 3. Construct headers side-by-side
            val topHeader = mutableListOf(
                HeaderItem("ល.រ"),
                HeaderItem("កូដសូចនាករ"),
                HeaderItem("សូចនាករ"),
                HeaderItem("អង្គភាព")
            )
            years.forEach { y -> topHeader.add(HeaderItem(y)) }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាលមន្ទីរកសិកម្ម រុក្ខាប្រមាញ់ និងនេសាទ",
                sub_title = "17B11 ស្ថិតិសហគមន៍អភិវឌ្ឍន៍កសិកម្ម ជលផល និងព្រៃឈើ",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }
}