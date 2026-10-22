package com.kh.niswebportal.app.nis61.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis61t11InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_61t11"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT sector, year, total_persons, total_female, workplace_injury_total,
                       workplace_injury_female, commute_injury_total, commute_injury_female
                FROM nis_61t11_occupational_injuries
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
                                rs.getString("sector") ?: "",
                                rs.getString("year") ?: "",
                                rs.getString("total_persons") ?: "-",
                                rs.getString("total_female") ?: "-",
                                rs.getString("workplace_injury_total") ?: "-",
                                rs.getString("workplace_injury_female") ?: "-",
                                rs.getString("commute_injury_total") ?: "-",
                                rs.getString("commute_injury_female") ?: "-"
                            )
                        )
                    }
                }
            }

            val headers = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("វិស័យ (Sector)"),
                HeaderItem("ឆ្នាំ (Year)"),
                HeaderItem("សរុប (Total Persons)"),
                HeaderItem("ស្រី (Total Female)"),
                HeaderItem("គ្រោះថ្នាក់កន្លែងធ្វើការ - សរុប (Workplace Injury Total)"),
                HeaderItem("គ្រោះថ្នាក់កន្លែងធ្វើការ - ស្រី (Workplace Injury Female)"),
                HeaderItem("គ្រោះថ្នាក់ពេលធ្វើដំណើរ - សរុប (Commute Injury Total)"),
                HeaderItem("គ្រោះថ្នាក់ពេលធ្វើដំណើរ - ស្រី (Commute Injury Female)")
            )

            return DynamicReportResponse(
                title = "ស្ថិតិជំងឺវិជ្ជាជីវៈ និងគ្រោះថ្នាក់ការងារប្រចាំឆ្នាំ (Annual Occupational Diseases and Employment Injuries Statistics)",
                sub_title = "NIS 61T_11 - ស្ថិតិទីផ្សារការងារនិងកម្លាំងពលកម្ម",
                tb_headers = listOf(headers),
                tb_rows = rows
            )
        }
    }
}