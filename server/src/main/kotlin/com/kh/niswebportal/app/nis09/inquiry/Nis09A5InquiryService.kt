package com.kh.niswebportal.app.nis09.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis09A5InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_09a5"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    item_code,
                    country_kh,
                    country_en,
                    city_kh,
                    city_en,
                    diplomatic_days,
                    official_days,
                    special_days,
                    ordinary_days,
                    effective_date
                FROM nis_09a5_visa_exemptions
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
                                rs.getString("item_code")?.replace("09A5_", ""), // Display only the numeric ID
                                rs.getString("country_kh"),
                                rs.getString("country_en"),
                                rs.getString("city_kh"),
                                rs.getString("city_en"),
                                rs.getString("diplomatic_days"),
                                rs.getString("official_days"),
                                rs.getString("special_days"),
                                rs.getString("ordinary_days"),
                                rs.getString("effective_date")
                            )
                        )
                    }
                }
            }

            // 1. Construct 2-Tier Headers
            val topHeader = listOf(
                HeaderItem(title = "\u200B"), // ล.ร
                HeaderItem(title = "\u200B"), // Country Kh
                HeaderItem(title = "\u200B"), // Country En
                HeaderItem(title = "\u200B"), // City Kh
                HeaderItem(title = "\u200B"), // City En
                HeaderItem(title = "ចំនួនថ្ងៃអនុញាតតាមប្រភេទវីសារ"), // Parent Header: Visa Days
                HeaderItem(title = ""),       // Spanning...
                HeaderItem(title = ""),       // Spanning...
                HeaderItem(title = ""),       // Spanning...
                HeaderItem(title = "\u200B")  // Effective Date
            )

            val subHeader = listOf(
                HeaderItem(title = "ល.រ"),
                HeaderItem(title = "ឈ្មោះប្រទេសជាភាសាខ្មែរ"),
                HeaderItem(title = "ឈ្មោះប្រទេសជាភាសាអង់គ្លេស"),
                HeaderItem(title = "ឈ្មោះទីក្រុងជាភាសាខ្មែរ"),
                HeaderItem(title = "ឈ្មោះទីក្រុងអង់គ្លេស"),
                HeaderItem(title = "ការទូត"),    // Diplomatic
                HeaderItem(title = "ផ្លូវការ"),    // Official
                HeaderItem(title = "ពិសេស"),    // Special
                HeaderItem(title = "ធម្មតា"),     // Ordinary
                HeaderItem(title = "ថ្ងៃចាប់អនុវត្ត")
            )

            return DynamicReportResponse(
                title = "ក្រសួងការបរទេស និងសហប្រតិបត្តិការអន្តរជាតិ",
                sub_title = "09A5 ស្ថិតិប្រទេសលើកលែងទិដ្ឋាការជាមួយកម្ពុជា ប្រចាំឆ្នាំ", // Visa Exemption Countries
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = rows
            )
        }
    }
}