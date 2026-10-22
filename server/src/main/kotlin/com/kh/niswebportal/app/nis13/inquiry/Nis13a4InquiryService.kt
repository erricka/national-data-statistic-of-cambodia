package com.kh.niswebportal.app.nis13.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis13a4InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_13a4"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT item_code, description, unit, period_type, data_value, remarks
                FROM nis_13a4_electricity_customers
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(dataSql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var idx = 1
                    while (rs.next()) {
                        rows.add(
                            listOf(
                                idx++,
                                rs.getString("item_code") ?: "",
                                rs.getString("description") ?: "",
                                rs.getString("unit") ?: "-",
                                rs.getString("period_type") ?: "-",
                                rs.getString("data_value") ?: "-",
                                rs.getString("remarks") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("កូដ (Code)"),
                HeaderItem("បរិយាយទិន្នន័យ (Description)"),
                HeaderItem("ឯកតា (Unit)"),
                HeaderItem("រយៈពេល (Period)"),
                HeaderItem("ទិន្នន័យ (Data Value)"),
                HeaderItem("ផ្សេងៗ (Remarks)")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យចំនួនអតិថិជនដែលទទួលបានការផ្គត់ផ្គង់អគ្គិសនីប្រចាំឆ្នាំ (Total Number of Electricity Customers)",
                sub_title = "NIS 13A_4 - ស្ថិតិរដ្ឋបាលក្រសួងរ៉ែនិងថាមពល (វិស័យថាមពល)",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}