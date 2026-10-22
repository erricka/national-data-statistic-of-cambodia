package com.kh.niswebportal.app.nis22.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis22A2InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_22a2"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    year, 
                    num_mobile_phones, 
                    doms_conversations_k_min, 
                    inter_conversations_sent, 
                    inter_conversations_recv
                FROM nis_22a2_telecommunication_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY year ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        rows.add(
                            listOf(
                                rs.getInt("year"),
                                rs.getNullableLong("num_mobile_phones"),
                                rs.getNullableLong("doms_conversations_k_min"),
                                rs.getNullableLong("inter_conversations_sent"),
                                rs.getNullableLong("inter_conversations_recv")
                            )
                        )
                    }
                }
            }

            val headerRow = listOf(
                HeaderItem("Year"),
                HeaderItem("Number of Mobile Phones"),
                HeaderItem("Domestic Conversations ('000 mins)"),
                HeaderItem("International Sent"),
                HeaderItem("International Received")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងប្រៃសណីយ៍ និងទូរគមនាគមន៍",
                sub_title = "22a2 ស្ថិតិទូរគមនាគមន៍",
                tb_headers = listOf(headerRow),
                tb_rows = rows
            )
        }
    }

    private fun ResultSet.getNullableLong(columnName: String): Long? {
        val value = this.getLong(columnName)
        return if (this.wasNull()) null else value
    }
}