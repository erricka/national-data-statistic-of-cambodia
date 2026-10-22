package com.kh.niswebportal.app.nis09.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis09A3InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_09a3"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
// Sort by id to preserve the exact entry order of the source file
            val sql = """
                SELECT 
                    item_code,
                    agency_name,
                    acronym,
                    est_hq,
                    representative,
                    poc,
                    address
                FROM nis_09a3_un_agencies
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        rows.add(
                            listOf(
                                rs.getString("item_code")?.replace("09A3_", ""), // Extracts just the number
                                rs.getString("agency_name"),
                                rs.getString("acronym"),
                                rs.getString("est_hq"),
                                rs.getString("representative"),
                                rs.getString("poc"),
                                rs.getString("address")
                            )
                        )
                    }
                }
            }

            // Flat, single-tier headers perfectly mapping your 7 columns
            val headerRow = listOf(
                HeaderItem(title = "ល.រ"),
                HeaderItem(title = "ទីភ្នាក់ងារអង្គការសហប្រជាជាតិ"),
                HeaderItem(title = "អក្សរកាត់"),
                HeaderItem(title = "ឆ្នាំបង្កើត/ទីស្នាក់ការ"),
                HeaderItem(title = "តំណាងប្រចាំកម្ពុជា"),
                HeaderItem(title = "Point of Contact"),
                HeaderItem(title = "អាសយដ្ឋាន")
            )

            return DynamicReportResponse(
                title = "ក្រសួងការបរទេស និងសហប្រតិបត្តិការអន្តរជាតិ",
                sub_title = "09A3 ស្ថិតិទីភ្នាក់ងារអង្គការសហប្រជាជាតិប្រចាំកម្ពុជា ប្រចាំឆ្នាំ", // UN Agencies in Cambodia
                tb_headers = listOf(headerRow),
                tb_rows = rows
            )
        }
    }
}