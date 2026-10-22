package com.kh.niswebportal.app.nis16.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis16a1InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_16a1"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    academic_year,
                    sch_kg,
                    sch_primary,
                    sch_lower_sec,
                    sch_upper_sec,
                    sch_total,
                    cls_kg,
                    cls_primary,
                    cls_lower_sec,
                    cls_upper_sec,
                    cls_total
                FROM nis_16a1
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY academic_year ASC
            """.trimIndent()

            // 1. Construct 2-Tier Headers matching the official structure
            val topHeader = listOf(
                HeaderItem(title = "ល.រ"),
                HeaderItem(title = "ឆ្នាំសិក្សា"),
                HeaderItem(title = "សាលារៀន"),
                HeaderItem(title = ""),
                HeaderItem(title = ""),
                HeaderItem(title = ""),
                HeaderItem(title = ""),
                HeaderItem(title = "ថ្នាក់រៀន"),
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
                HeaderItem(title = "សរុប"),
                HeaderItem(title = "មតេយ្យ"),
                HeaderItem(title = "បឋម"),
                HeaderItem(title = "អនុវិទ្យាល័យ"),
                HeaderItem(title = "វិទ្យាល័យ"),
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
                            rs.getString("sch_kg") ?: "-",
                            rs.getString("sch_primary") ?: "-",
                            rs.getString("sch_lower_sec") ?: "-",
                            rs.getString("sch_upper_sec") ?: "-",
                            rs.getString("sch_total") ?: "-",
                            rs.getString("cls_kg") ?: "-",
                            rs.getString("cls_primary") ?: "-",
                            rs.getString("cls_lower_sec") ?: "-",
                            rs.getString("cls_upper_sec") ?: "-",
                            rs.getString("cls_total") ?: "-"
                        )
                        tbRows.add(row)
                    }
                }
            }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល អប់រំ យុវជន និងកីឡា",
                sub_title = "16A1 ចំនួនសាលារៀន ថ្នាក់រៀន ក្នុងប្រព័ន្ធអប់រំចំណេះទូទៅ តាមឆ្នាំសិក្សា",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = tbRows
            )
        }
    }
}