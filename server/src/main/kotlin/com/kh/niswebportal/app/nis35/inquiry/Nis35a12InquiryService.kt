package com.kh.niswebportal.app.nis35.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis35a12InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_35a12"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    period,
                    num_atm_terminal,
                    num_pos,
                    num_debit_card,
                    num_credit_card
                FROM nis_35a12_atm_pos_cards
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()
                        row.add(index++)
                        row.add(rs.getString("period") ?: "-")
                        row.add(rs.getString("num_atm_terminal") ?: "-")
                        row.add(rs.getString("num_pos") ?: "-")
                        row.add(rs.getString("num_debit_card") ?: "-")
                        row.add(rs.getString("num_credit_card") ?: "-")
                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("Date"),
                HeaderItem("ATM Number of ATM terminal"),
                HeaderItem("POS Number of POS"),
                HeaderItem("Debit card (# of card)"),
                HeaderItem("Credit card (# of card)")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ធនាគារជាតិនៃកម្ពុជា",
                sub_title = "35A12 Number of ATM Terminals, POS, Debit Cards, and Credit Cards",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }
}