package com.kh.niswebportal.app.nis18.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis18a2InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_18a2"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val years = mutableListOf<String>()
            val yearSql = """
                SELECT DISTINCT year 
                FROM nis_18a2_cultural_site_tourist_stats
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
                MAX(CASE WHEN year = '$y' THEN domestic_visitors END) AS dom_$y,
                MAX(CASE WHEN year = '$y' THEN international_visitors END) AS int_$y
                """.trimIndent()
            }

            val sql = """
                SELECT 
                    province_code,
                    province_kh,
                    site_code,
                    site_kh
                    ${if (years.isNotEmpty()) ", $yearSelects" else ""}
                FROM nis_18a2_cultural_site_tourist_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                GROUP BY province_code, province_kh, site_code, site_kh
                ORDER BY province_kh ASC, site_kh ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(index++)
                        row.add(rs.getString("province_code") ?: "-")
                        row.add(rs.getString("province_kh") ?: "-")
                        row.add(rs.getString("site_code"))
                        row.add(rs.getString("site_kh"))

                        for (y in years) {
                            row.add(rs.getString("dom_$y") ?: "-")
                            row.add(rs.getString("int_$y") ?: "-")
                        }

                        rows.add(row)
                    }
                }
            }

            val topHeader = mutableListOf(
                HeaderItem("ល.រ"),
                HeaderItem("កូដខេត្ត"),
                HeaderItem("រាជធានី-ខេត្ត"),
                HeaderItem("កូដទីតាំង"),
                HeaderItem("តំបន់បេតិកភណ្ឌ")
            )

            val subHeader = mutableListOf<HeaderItem>()

            years.forEach { y ->
                topHeader.add(HeaderItem("ឆ្នាំ $y"))
                subHeader.add(HeaderItem("ភ្ញៀវជាតិ"))
                subHeader.add(HeaderItem("ភ្ញៀវអន្តរជាតិ"))
            }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងវប្បធម៌វិចិត្រសិល្បៈ",
                sub_title = "18A2 ស្ថិតិភ្ញៀវទេសចរនៅទីតាំងវប្បធម៌សំខាន់ៗតាមរាជធានី-ខេត្ត",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = rows
            )
        }
    }
}