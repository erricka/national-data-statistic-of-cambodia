package com.kh.niswebportal.app.nis22.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis22A1InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_22a1"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val years = mutableListOf<Int>()
            val yearSql = """
                SELECT year 
                FROM nis_22a1_postal_services
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                GROUP BY year 
                ORDER BY year ASC
            """.trimIndent()

            conn.prepareStatement(yearSql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        years.add(rs.getInt("year"))
                    }
                }
            }

            if (years.isEmpty()) {
                return DynamicReportResponse(
                    title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងប្រៃសណីយ៍ និងទូរគមនាគមន៍",
                    sub_title = "22a1 ស្ថិតិសេវាកម្ម និងសាជីវកម្មប្រៃសណីយ៍",
                    tb_headers = emptyList(),
                    tb_rows = emptyList()
                )
            }

            val selectAggregates = years.indices.joinToString(", ") { i ->
                "MAX(CASE WHEN year = ${years[i]} THEN amount END) AS y_${i}_amt"
            }

            val pivotSql = """
                SELECT metric_code, item_no, description_english, category, $selectAggregates
                FROM nis_22a1_postal_services
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                GROUP BY metric_code, item_no, description_english, category
                ORDER BY metric_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(pivotSql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()
                        row.add(rs.getString("metric_code"))
                        row.add(rs.getString("item_no"))
                        row.add(rs.getString("description_english"))
                        row.add(rs.getString("category"))

                        for (i in years.indices) {
                            row.add(rs.getNullableLong("y_${i}_amt"))
                        }
                        rows.add(row)
                    }
                }
            }

            val topHeader = mutableListOf<HeaderItem>()
            topHeader.add(HeaderItem("Metric ID"))
            topHeader.add(HeaderItem("No."))
            topHeader.add(HeaderItem("Description"))
            topHeader.add(HeaderItem("Category"))

            for (yr in years) {
                topHeader.add(HeaderItem(yr.toString()))
            }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងប្រៃសណីយ៍ និងទូរគមនាគមន៍",
                sub_title = "22a1 ស្ថិតិសេវាកម្ម និងសាជីវកម្មប្រៃសណីយ៍",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }

    private fun ResultSet.getNullableLong(columnName: String): Long? {
        val value = this.getLong(columnName)
        return if (this.wasNull()) null else value
    }
}