package com.kh.niswebportal.app.nis18.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis18a10InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_18a10"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val years = mutableListOf<String>()
            val yearSql = """
                SELECT DISTINCT year 
                FROM nis_18a10_library_and_bookstore_stats
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
                "MAX(CASE WHEN year = '$y' THEN total_count END) AS count_$y"
            }

            val sql = """
                SELECT 
                    type_code,
                    type_kh
                    ${if (years.isNotEmpty()) ", $yearSelects" else ""}
                FROM nis_18a10_library_and_bookstore_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                GROUP BY type_code, type_kh
                ORDER BY type_kh ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(index++)
                        row.add(rs.getString("type_code"))
                        row.add(rs.getString("type_kh"))

                        for (y in years) {
                            row.add(rs.getString("count_$y") ?: "-")
                        }

                        rows.add(row)
                    }
                }
            }

            val header = mutableListOf(
                HeaderItem("ល.រ"),
                HeaderItem("កូដ"),
                HeaderItem("ប្រភេទ")
            )

            years.forEach { y ->
                header.add(HeaderItem("ឆ្នាំ$y"))
            }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងវប្បធម៌វិចិត្រសិល្បៈ",
                sub_title = "18A10 ស្ថិតិបណ្ណាល័យនិងបណ្ណាគារទូទាំងប្រទេសប្រចាំឆ្នាំ",
                tb_headers = listOf(header),
                tb_rows = rows
            )
        }
    }
}