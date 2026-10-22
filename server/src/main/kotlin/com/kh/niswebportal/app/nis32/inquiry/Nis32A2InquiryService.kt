package com.kh.niswebportal.app.nis32.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis32A2InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_32a2"

    override fun getDynamicReport(uploadId: String? ): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    year,
                    category_code,
                    category_name_kh,
                    unit,
                    total_passengers
                FROM nis_32a2_air_passengers
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY year ASC, category_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(index++)
                        row.add(rs.getInt("year"))
                        row.add(rs.getString("category_code"))
                        row.add(rs.getString("category_name_kh"))
                        row.add(rs.getString("unit"))
                        row.add(rs.getNullableInt("total_passengers") ?: 0)

                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ឆ្នាំ"),
                HeaderItem("កូដ"),
                HeaderItem("ប្រភេទអ្នកធ្វើដំណើរ"),
                HeaderItem("ខ្នាត"),
                HeaderItem("ចំនួនអ្នកដំណើរ (នាក់)")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល លេខាធិការដ្ឋានអាកាសចរណ៍ស៊ីវិល",
                sub_title = "32A2 ស្ថិតិដឹកជញ្ជូនតាមផ្លូវអាកាស(អ្នកដំណើរ ជើងហោះហើរ ប្រចាំខែ ត្រីមាស និងប្រចាំឆ្នាំ)",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }

    private fun ResultSet.getNullableInt(columnName: String): Int? {
        val value = this.getInt(columnName)
        return if (this.wasNull()) null else value
    }
}