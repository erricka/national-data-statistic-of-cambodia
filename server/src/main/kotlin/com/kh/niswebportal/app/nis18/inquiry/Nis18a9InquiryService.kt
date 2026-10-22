package com.kh.niswebportal.app.nis18.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis18a9InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_18a9"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()

            // 1. Fetch dynamic list of distinct years
            val years = mutableListOf<String>()
            val yearSql = """
                SELECT DISTINCT year 
                FROM nis_18a9_art_objects_stats
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
                "MAX(CASE WHEN year = '$y' THEN quantity END) AS year_$y"
            }

            val sql = """
                SELECT 
                    art_object_type
                    ${if (years.isNotEmpty()) ", $yearSelects" else ""}
                FROM nis_18a9_art_objects_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                GROUP BY art_object_type
                ORDER BY art_object_type ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)

                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(index++)
                        row.add(rs.getString("art_object_type"))

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
                HeaderItem("ប្រភេទវត្ថុសិល្បៈ")
            )
            years.forEach { y -> topHeader.add(HeaderItem(y)) }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងវប្បធម៌វិចិត្រសិល្បៈ",
                sub_title = "18A9 ស្ថិតិត្រួតពិនិត្យវត្ថុសិល្បៈថ្មីនិងវត្ថុសិល្បៈចម្លងដូចសម្រាប់នាំចេញប្រចាំឆ្នាំ",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }
}