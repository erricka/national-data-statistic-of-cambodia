package com.kh.niswebportal.app.nis27.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis27ItsASraiaSaiAgrInquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_27_its_a_sraia_sai_agr"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()

            val years = mutableListOf<String>()
            val yearSql = """
                SELECT DISTINCT year 
                FROM public.nis_27_stats
                WHERE source_id = ? AND
                (${if (hasUploadId) "upload_id = ?" else "(upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"})
                ORDER BY year ASC
            """.trimIndent()

            conn.prepareStatement(yearSql).use { stmt ->
                stmt.setString(1, sourceId)
                if (hasUploadId) stmt.setString(2, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        years.add(rs.getString("year"))
                    }
                }
            }

            val yearSelects = years.joinToString(",\n") { y ->
                "MAX(CASE WHEN year = '$y' THEN metric_value END) AS year_$y"
            }

            val sql = """
                SELECT 
                    indicator_code,
                    indicator_name
                    ${if (years.isNotEmpty()) ", $yearSelects" else ""}
                FROM public.nis_27_stats
                WHERE source_id = ? AND
                (${if (hasUploadId) "upload_id = ?" else "(upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"})
                GROUP BY indicator_code, indicator_name
                ORDER BY indicator_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, sourceId)
                if (hasUploadId) stmt.setString(2, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(index++)
                        row.add(rs.getString("indicator_code"))
                        row.add(rs.getString("indicator_name"))

                        for (y in years) {
                            row.add(rs.getString("year_$y") ?: "-")
                        }
                        rows.add(row)
                    }
                }
            }

            val topHeader = mutableListOf(
                HeaderItem("ល.រ"),
                HeaderItem("កូដសូចនាករ"),
                HeaderItem("សូចនាករ")
            )
            years.forEach { y -> topHeader.add(HeaderItem(y)) }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងទេសចរណ៍",
                sub_title = "ស្ថិតិកំណើនទេសចរអន្តរជាតិអាកាសយានដ្ឋានអន្តរជាតិសៀមរាបអង្គរ តាមខែ (អត្រាកំណើនប្រចាំឆ្នាំ)",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }
}
