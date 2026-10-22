package com.kh.niswebportal.app.nis61.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis61t20InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_61t20"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT category_group, indicator_name, employed_formal, employed_informal,
                       employed_total, unemployed, outside_labour_force, labour_force_participation_rate
                FROM nis_61t20_labour_force_stats
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
                        val indicator = rs.getString("indicator_name") ?: ""

                        rows.add(
                            listOf(
                                idx++,
                                "[$group] $indicator",
                                rs.getString("employed_formal") ?: "-",
                                rs.getString("employed_informal") ?: "-",
                                rs.getString("employed_total") ?: "-",
                                rs.getString("unemployed") ?: "-",
                                rs.getString("outside_labour_force") ?: "-",
                                rs.getString("labour_force_participation_rate") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("លក្ខណៈប្រជាសាស្រ្ត / សូចនាករ (Demographic / Indicator)"),
                HeaderItem("អ្នកមានការងារធ្វើ - ក្នុងប្រព័ន្ធ (Employed - Formal)"),
                HeaderItem("អ្នកមានការងារធ្វើ - ក្រៅប្រព័ន្ធ (Employed - Informal)"),
                HeaderItem("អ្នកមានការងារធ្វើ - សរុប (Employed - Total)"),
                HeaderItem("អ្នកគ្មានការងារធ្វើ (Unemployed)"),
                HeaderItem("ក្រៅកម្លាំងពលកម្ម (Outside Labour Force)"),
                HeaderItem("អត្រាចូលរួមក្នុងកម្លាំងពលកម្ម (%) (Participation Rate)")
            )

            return DynamicReportResponse(
                title = "ស្ថិតិអង្កេតស្ថានភាពកម្លាំងពលកម្មនិងការងារនៅកម្ពុជាប្រចាំឆ្នាំ (Annual Cambodia Labour Force Survey Statistics)",
                sub_title = "NIS 61T_20 - ស្ថិតិទីផ្សារការងារនិងកម្លាំងពលកម្ម",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}