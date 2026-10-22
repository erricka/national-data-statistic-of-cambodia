package com.kh.niswebportal.app.nis61.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis61t25InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_61t25"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT category_group, employment_source, employers_recruiting_count,
                       job_vacancies_count, job_seekers_registered_count, job_placement_count
                FROM nis_61t25_job_opportunities_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(dataSql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var idx = 1
                    while (rs.next()) {
                        val group = rs.getString("category_group") ?: ""
                        val source = rs.getString("employment_source") ?: ""

                        rows.add(
                            listOf(
                                idx++,
                                "[$group] $source",
                                rs.getString("employers_recruiting_count") ?: "-",
                                rs.getString("job_vacancies_count") ?: "-",
                                rs.getString("job_seekers_registered_count") ?: "-",
                                rs.getString("job_placement_count") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ប្រភពព័ត៌មានការងារ (Employment Source / Region)"),
                HeaderItem("ចំនួននិយោជកដែលបានប្រកាសរើសបុគ្គលិក (Employers Recruiting)"),
                HeaderItem("ចំនួនកន្លែងការងារទំនេរដែលបានប្រកាស (Job Vacancies)"),
                HeaderItem("ចំនួនអ្នកចុះឈ្មោះស្វែងរកការងារធ្វើ (Job Seekers Registered)"),
                HeaderItem("ចំនួនអ្នកទទួលបានការងារតាមរយៈ NEA (Job Placements via NEA)")
            )

            return DynamicReportResponse(
                title = "ស្ថិតិអង្កេតព័ត៌មានឱកាសការងារប្រចាំឆមាស (Bi-annual Job Opportunities Information Statistics)",
                sub_title = "NIS 61T_25 - ស្ថិតិទីផ្សារការងារនិងកម្លាំងពលកម្ម",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}