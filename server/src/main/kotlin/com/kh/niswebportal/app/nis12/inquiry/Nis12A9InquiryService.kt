package com.kh.niswebportal.app.nis12.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis12A9InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_12a9"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val years = mutableListOf<Int>()
            val yearSql = """
                SELECT year 
                FROM nis_12a9_vitamin_a_children
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
                    title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងសុខាភិបាល",
                    sub_title = "12a9 ចំនួនកុមារអាយុ ៦-៥៩ ខែ បានទទួលគ្រាប់ថ្នាំជីវជាតិ អា(ដំំណាក់កាលទី២) តាមរាជធានី-ខេត្ត",
                    tb_headers = emptyList(),
                    tb_rows = emptyList()
                )
            }

            val selectAggregates = years.indices.joinToString(", ") { i ->
                "MAX(CASE WHEN year = ${years[i]} THEN metric_value END) AS y_${i}_val"
            }

            val pivotSql = """
                SELECT province_code, province_name, metric_code, metric_description, $selectAggregates
                FROM nis_12a9_vitamin_a_children
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                GROUP BY province_code, province_name, metric_code, metric_description
                ORDER BY province_code ASC, metric_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(pivotSql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()
                        row.add(rs.getString("province_code"))
                        row.add(rs.getString("province_name"))
                        row.add(rs.getString("metric_code"))
                        row.add(rs.getString("metric_description"))

                        for (i in years.indices) {
                            row.add(rs.getNullableDouble("y_${i}_val"))
                        }
                        rows.add(row)
                    }
                }
            }

            val topHeader = mutableListOf<HeaderItem>()
            topHeader.add(HeaderItem("Pro_Code"))
            topHeader.add(HeaderItem("រាជធានី ខេត្ត"))
            topHeader.add(HeaderItem("Metric Code"))
            topHeader.add(HeaderItem("Metric Description"))

            for (yr in years) {
                topHeader.add(HeaderItem(yr.toString()))
            }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងសុខាភិបាល",
                sub_title = "12a9 ចំនួនកុមារអាយុ ៦-៥៩ ខែ បានទទួលគ្រាប់ថ្នាំជីវជាតិ អា(ដំំណាក់កាលទី២) តាមរាជធានី-ខេត្ត",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }

    
}