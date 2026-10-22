package com.kh.niswebportal.app.nis18.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis18a7InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_18a7"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val years = mutableListOf<String>()
            val yearSql = """
                SELECT DISTINCT year 
                FROM nis_18a7_film_and_video_service_stats
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

            val yearSelects = years.joinToString(",\n") { y ->
                """
                MAX(CASE WHEN year = '$y' THEN title_count END) AS title_$y,
                MAX(CASE WHEN year = '$y' THEN license_count END) AS license_$y
                """.trimIndent()
            }

            val sql = """
                SELECT 
                    category_code,
                    category_kh,
                    sub_category_code,
                    sub_category_kh
                    ${if (years.isNotEmpty()) ", $yearSelects" else ""}
                FROM nis_18a7_film_and_video_service_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                GROUP BY category_code, category_kh, sub_category_code, sub_category_kh
                ORDER BY category_kh ASC, sub_category_kh ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(index++)
                        row.add(rs.getString("sub_category_code"))
                        row.add(rs.getString("category_kh") ?: "-")
                        row.add(rs.getString("sub_category_kh"))

                        for (y in years) {
                            row.add(rs.getString("title_$y") ?: "-")
                            row.add(rs.getString("license_$y") ?: "-")
                        }

                        rows.add(row)
                    }
                }
            }

            val topHeader = mutableListOf(
                HeaderItem("ល.រ"),
                HeaderItem("កូដ"),
                HeaderItem("ប្រភេទសេវា"),
                HeaderItem("សេវាលម្អិត")
            )

            val subHeader = mutableListOf<HeaderItem>()

            years.forEach { y ->
                topHeader.add(HeaderItem("ឆ្នាំ $y"))
                subHeader.add(HeaderItem("ចំណងជើង"))
                subHeader.add(HeaderItem("ច្បាប់"))
            }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងវប្បធម៌វិចិត្រសិល្បៈ",
                sub_title = "18A7 ស្ថិតិសេវាផ្នែកភាពយន្ត និងវីដេអូ",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = rows
            )
        }
    }
}