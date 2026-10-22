package com.kh.niswebportal.app.nis16.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis16c4InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_16c4"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
            val query = """
                SELECT budget_year, cyc_council, cyc_member, cyc_female, 
                       ccc_council, ccc_member, ccc_female
                FROM nis_16c4_youth_council_statistics
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
                            rs.getString("cyc_council") ?: "-",
                            rs.getString("cyc_member") ?: "-",
                            rs.getString("cyc_female") ?: "-",
                            rs.getString("ccc_council") ?: "-",
                            rs.getString("ccc_member") ?: "-",
                            rs.getString("ccc_female") ?: "-"
                        )
                        tbRows.add(row)
                    }
                }
            }

            // Creating single-level headers matching the exact HeaderItem definition without rowspan/colspan
            val topHeader = listOf(
                HeaderItem(title = "ល.រ"),
                HeaderItem(title = "ឆ្នាំ"),
                HeaderItem(title = "ក្រុមប្រឹក្សាយុវជនកម្ពុជា - ក្រុមប្រឹក្សា"),
                HeaderItem(title = "ក្រុមប្រឹក្សាយុវជនកម្ពុជា - សមាជិកសរុប"),
                HeaderItem(title = "ក្រុមប្រឹក្សាយុវជនកម្ពុជា - ស្រី"),
                HeaderItem(title = "ក្រុមប្រឹក្សាកុមារកម្ពុជា - ក្រុមប្រឹក្សា"),
                HeaderItem(title = "ក្រុមប្រឹក្សាកុមារកម្ពុជា - សមាជិកសរុប"),
                HeaderItem(title = "ក្រុមប្រឹក្សាកុមារកម្ពុជា - ស្រី")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល អប់រំ យុវជន និងកីឡា",
                sub_title = "ស្ថិតិក្រុមប្រឹក្សាយុវជនកម្ពុជា និងក្រុមប្រឹក្សាកុមារកម្ពុជា",
                tb_headers = listOf(topHeader),
                tb_rows = tbRows
            )
        }
    }
}