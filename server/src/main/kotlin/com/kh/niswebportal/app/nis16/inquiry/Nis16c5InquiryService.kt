package com.kh.niswebportal.app.nis16.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis16c5InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_16c5"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
            val query = """
                SELECT budget_year, cub_female, cub_total, sct_female, sct_total, 
                       rvr_female, rvr_total, ldr_female, ldr_total
                FROM nis_16c5_scout_membership
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY budget_year ASC
            """.trimIndent()

            val tbRows = mutableListOf<List<Any?>>()
            var idx = 1

            conn.prepareStatement(query).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val row = mutableListOf<Any?>(
                            idx++,
                            rs.getString("budget_year") ?: "-",
                            rs.getString("cub_female") ?: "-",
                            rs.getString("cub_total") ?: "-",
                            rs.getString("sct_female") ?: "-",
                            rs.getString("sct_total") ?: "-",
                            rs.getString("rvr_female") ?: "-",
                            rs.getString("rvr_total") ?: "-",
                            rs.getString("ldr_female") ?: "-",
                            rs.getString("ldr_total") ?: "-"
                        )
                        tbRows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem(title = "ល.រ"),
                HeaderItem(title = "ឆ្នាំ"),
                HeaderItem(title = "កាយរឹទ្ធិប្អូន - ស្រី"),
                HeaderItem(title = "កាយរឹទ្ធិប្អូន - សរុប"),
                HeaderItem(title = "កាយរឹទ្ធិជំទង់ - ស្រី"),
                HeaderItem(title = "កាយរឹទ្ធិជំទង់ - សរុប"),
                HeaderItem(title = "កាយរឹទ្ធិរៀមច្បង - ស្រី"),
                HeaderItem(title = "កាយរឹទ្ធិរៀមច្បង - សរុប"),
                HeaderItem(title = "មេដឹកនាំកាយរឹទ្ធិ - ស្រី"),
                HeaderItem(title = "មេដឹកនាំកាយរឹទ្ធិ - សរុប")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល អប់រំ យុវជន និងកីឡា",
                sub_title = "សមាជិកភាពកាយរឹទ្ធិជាតិកម្ពុជា",
                tb_headers = listOf(topHeader),
                tb_rows = tbRows
            )
        }
    }
}