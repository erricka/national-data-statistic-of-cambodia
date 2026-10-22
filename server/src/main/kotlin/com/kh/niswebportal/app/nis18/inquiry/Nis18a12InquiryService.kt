package com.kh.niswebportal.app.nis18.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis18a12InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_18a12"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    year,
                    short_term_arts_trainees,
                    ancestral_vocational_students
                FROM nis_18a12_art_training_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY year ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(index++)
                        row.add(rs.getString("year"))
                        row.add(rs.getString("short_term_arts_trainees") ?: "-")
                        row.add(rs.getString("ancestral_vocational_students") ?: "-")

                        rows.add(row)
                    }
                }
            }

            val header = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ឆ្នាំ"),
                HeaderItem("ចំនួនសិក្ខាកាមចូលរួមការបណ្តុះបណ្តាលជំនាញសិល្បៈខ្លីៗ"),
                HeaderItem("ចំនួនសិស្សចូលរួមការបណ្តុះបណ្តាលជំនាញវិជ្ជាជីវៈដូនតា")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងវប្បធម៌វិចិត្រសិល្បៈ",
                sub_title = "18A12 ស្ថិតិទាក់ទងនឹងការបណ្តុះបណ្តាលជំនាញសិល្បៈវប្បធម៌ប្រចាំឆ្នាំ",
                tb_headers = listOf(header),
                tb_rows = rows
            )
        }
    }
}