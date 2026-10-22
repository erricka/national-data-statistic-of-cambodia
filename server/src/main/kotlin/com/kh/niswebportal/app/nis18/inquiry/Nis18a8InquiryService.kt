package com.kh.niswebportal.app.nis18.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis18a8InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_18a8"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val years = mutableListOf<String>()
            val yearSql = """
                SELECT DISTINCT year 
                FROM nis_18a8_ancient_site_stats
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
                MAX(CASE WHEN year = '$y' THEN temple_foundation_count END) AS temple_$y,
                MAX(CASE WHEN year = '$y' THEN burial_mound_count END) AS burial_$y,
                MAX(CASE WHEN year = '$y' THEN residential_mound_count END) AS res_$y,
                MAX(CASE WHEN year = '$y' THEN irrigation_system_count END) AS irrig_$y,
                MAX(CASE WHEN year = '$y' THEN other_count END) AS oth_$y
                """.trimIndent()
            }

            val sql = """
                SELECT 
                    province_code,
                    province_kh
                    ${if (years.isNotEmpty()) ", $yearSelects" else ""}
                FROM nis_18a8_ancient_site_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                GROUP BY province_code, province_kh
                ORDER BY province_kh ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(index++)
                        row.add(rs.getString("province_code"))
                        row.add(rs.getString("province_kh"))

                        for (y in years) {
                            row.add(rs.getString("temple_$y") ?: "-")
                            row.add(rs.getString("burial_$y") ?: "-")
                            row.add(rs.getString("res_$y") ?: "-")
                            row.add(rs.getString("irrig_$y") ?: "-")
                            row.add(rs.getString("oth_$y") ?: "-")
                        }

                        rows.add(row)
                    }
                }
            }

            val topHeader = mutableListOf(
                HeaderItem("ល.រ"),
                HeaderItem("កូដ"),
                HeaderItem("រាជធានី-ខេត្ត")
            )

            val subHeader = mutableListOf<HeaderItem>()

            years.forEach { y ->
                topHeader.add(HeaderItem("ប្រភេទស្ថានីយ ឆ្នាំ$y"))
                subHeader.add(HeaderItem("ប្រាសាទមានគ្រឹះ"))
                subHeader.add(HeaderItem("ទួលកប់សព"))
                subHeader.add(HeaderItem("ទួលរស់នៅ"))
                subHeader.add(HeaderItem("ប្រព័ន្ធធារាសាស្រ្ត"))
                subHeader.add(HeaderItem("ផ្សេងៗ"))
            }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងវប្បធម៌វិចិត្រសិល្បៈ",
                sub_title = "18A8 ស្ថិតិស្ថានីយបុរាណ និងសំណង់បុរាណទូទាំងប្រទេសប្រចាំឆ្នាំ",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = rows
            )
        }
    }
}