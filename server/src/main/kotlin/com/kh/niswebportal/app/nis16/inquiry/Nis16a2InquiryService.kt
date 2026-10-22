package com.kh.niswebportal.app.nis16.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis16a2InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_16a2"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    academic_year,
                    stu_kg,
                    stu_primary,
                    stu_lower_sec,
                    stu_upper_sec,
                    stu_lower_sec_h,
                    stu_upper_sec_h,
                    stu_total,
                    edustf_kg,
                    edustf_primary,
                    edustf_lower_sec,
                    edustf_upper_sec,
                    edustf_lower_sec_h,
                    edustf_upper_sec_h,
                    edustf_total
                FROM nis_16a2
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY academic_year ASC
            """.trimIndent()

            // 1. Construct 2-Tier Headers matching the official structure
            val topHeader = listOf(
                HeaderItem(title = "ល.រ"),
                HeaderItem(title = "ឆ្នាំសិក្សា"),
                HeaderItem(title = "សិស្ស"),
                HeaderItem(title = ""),
                HeaderItem(title = ""),
                HeaderItem(title = ""),
                HeaderItem(title = ""),
                HeaderItem(title = ""),
                HeaderItem(title = ""),
                HeaderItem(title = "បុគ្គលិកអប់រំ"),
                HeaderItem(title = ""),
                HeaderItem(title = ""),
                HeaderItem(title = ""),
                HeaderItem(title = ""),
                HeaderItem(title = ""),
                HeaderItem(title = "")
            )

            val subHeader = listOf(
                HeaderItem(title = "ល.រ"),
                HeaderItem(title = "ឆ្នាំសិក្សា"),
                HeaderItem(title = "មតេយ្យ"),
                HeaderItem(title = "បឋម"),
                HeaderItem(title = "អនុវិទ្យាល័យ"),
                HeaderItem(title = "វិទ្យាល័យ"),
                HeaderItem(title = "មធ្យមសិក្សាបឋមភូមិ"),
                HeaderItem(title = "មធ្យមសិក្សាទុតិយភូមិ"),
                HeaderItem(title = "សរុប"),
                HeaderItem(title = "មតេយ្យ"),
                HeaderItem(title = "បឋម"),
                HeaderItem(title = "អនុវិទ្យាល័យ"),
                HeaderItem(title = "វិទ្យាល័យ"),
                HeaderItem(title = "មធ្យមសិក្សាបឋមភូមិ"),
                HeaderItem(title = "មធ្យមសិក្សាទុតិយភូមិ"),
                HeaderItem(title = "សរុប")
            )

            val tbRows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = listOf<Any?>(
                            index++,
                            rs.getString("academic_year") ?: "-",
                            rs.getString("stu_kg") ?: "-",
                            rs.getString("stu_primary") ?: "-",
                            rs.getString("stu_lower_sec") ?: "-",
                            rs.getString("stu_upper_sec") ?: "-",
                            rs.getString("stu_lower_sec_h") ?: "-",
                            rs.getString("stu_upper_sec_h") ?: "-",
                            rs.getString("stu_total") ?: "-",
                            rs.getString("edustf_kg") ?: "-",
                            rs.getString("edustf_primary") ?: "-",
                            rs.getString("edustf_lower_sec") ?: "-",
                            rs.getString("edustf_upper_sec") ?: "-",
                            rs.getString("edustf_lower_sec_h") ?: "-",
                            rs.getString("edustf_upper_sec_h") ?: "-",
                            rs.getString("edustf_total") ?: "-"
                        )
                        tbRows.add(row)
                    }
                }
            }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល អប់រំ យុវជន និងកីឡា",
                sub_title = "16A2 ចំនួនសិស្ស និងបុគ្គលិកអប់រំ ក្នុងប្រព័ន្ធអប់រំចំណេះទូទៅ តាមឆ្នាំសិក្សា",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = tbRows
            )
        }
    }
}