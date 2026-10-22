package com.kh.niswebportal.app.nis22.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis22A5InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_22a5"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val years = mutableListOf<Int>()
            val yearSql = """
                SELECT year 
                FROM nis_22a5_optical_fiber_network
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
                    sub_title = "22a5 ស្ថិតិបណ្តាញខ្សែកាបអ៊ុបទិកជាតិ",
                    tb_headers = emptyList(),
                    tb_rows = emptyList()
                )
            }

            val selectAggregates = years.indices.joinToString(", ") { i ->
                "MAX(CASE WHEN year = ${years[i]} THEN length_km END) AS y_${i}_val"
            }

            val pivotSql = """
                SELECT metric_code, description_english, $selectAggregates
                FROM nis_22a5_optical_fiber_network
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                GROUP BY metric_code, description_english
                ORDER BY metric_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(pivotSql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()
                        row.add(rs.getString("metric_code"))
                        row.add(rs.getString("description_english"))

                        for (i in years.indices) {
                            row.add(rs.getNullableDouble("y_${i}_val"))
                        }
                        rows.add(row)
                    }
                }
            }

            val topHeader = mutableListOf<HeaderItem>()
            topHeader.add(HeaderItem("ID"))
            topHeader.add(HeaderItem("Nation Optical Fiber Backbone Network"))

            for (yr in years) {
                topHeader.add(HeaderItem(yr.toString()))
            }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងប្រៃសណីយ៍ និងទូរគមនាគមន៍",
                sub_title = "22a5 ស្ថិតិបណ្តាញខ្សែកាបអ៊ុបទិកជាតិ",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }

    
}