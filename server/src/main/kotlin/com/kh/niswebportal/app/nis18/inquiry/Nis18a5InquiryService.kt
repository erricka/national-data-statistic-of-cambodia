package com.kh.niswebportal.app.nis18.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis18a5InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_18a5"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val years = mutableListOf<String>()
            val yearSql = """
                SELECT DISTINCT year 
                FROM nis_18a5_book_and_reading_service_stats
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
                MAX(CASE WHEN year = '$y' THEN isbn_number END) AS isbn_$y,
                MAX(CASE WHEN year = '$y' THEN visitor_count END) AS visitor_$y
                """.trimIndent()
            }

            val sql = """
                SELECT 
                    service_code,
                    service_kh
                    ${if (years.isNotEmpty()) ", $yearSelects" else ""}
                FROM nis_18a5_book_and_reading_service_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                GROUP BY service_code, service_kh
                ORDER BY service_kh ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(index++)
                        row.add(rs.getString("service_code"))
                        row.add(rs.getString("service_kh"))

                        for (y in years) {
                            row.add(rs.getString("title_$y") ?: "-")
                            row.add(rs.getString("isbn_$y") ?: "-")
                            row.add(rs.getString("visitor_$y") ?: "-")
                        }

                        rows.add(row)
                    }
                }
            }

            val topHeader = mutableListOf(
                HeaderItem("ល.រ"),
                HeaderItem("កូដ"),
                HeaderItem("ឈ្មោះសេវា")
            )

            val subHeader = mutableListOf<HeaderItem>()

            years.forEach { y ->
                topHeader.add(HeaderItem("ឆ្នាំ $y"))
                subHeader.add(HeaderItem("ចំណងជើង"))
                subHeader.add(HeaderItem("លេខ"))
                subHeader.add(HeaderItem("នាក់"))
            }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងវប្បធម៌វិចិត្រសិល្បៈ",
                sub_title = "18A5 សេវាផ្នែកសៀវភៅនិងការអាន",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = rows
            )
        }
    }
}