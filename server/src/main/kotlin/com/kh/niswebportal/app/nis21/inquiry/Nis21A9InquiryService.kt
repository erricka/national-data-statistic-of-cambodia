package com.kh.niswebportal.app.nis21.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis21A9InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_21a9"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val years = mutableListOf<Int>()
            val yearSql = """
                SELECT year 
                FROM nis_21a9_children_under_two
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
                    title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងសង្គមកិច្ច អតីតយុទ្ធជន និងយុវនីតិសម្បទា",
                    sub_title = "21A9 ស្ថិតិចំនួនកុមារអាយុក្រោម ២ឆ្នាំ នៅក្នុងគ្រួសារមានបណ្ណសមធម៌ ទទួលបានប្រាក់ឧបត្ថម្ភពីកម្មវិធីជាតិជំនួយសង្គមប្រចាំឆ្នាំ",
                    tb_headers = emptyList(),
                    tb_rows = emptyList()
                )
            }

            val selectAggregates = years.indices.joinToString(", ") { i ->
                "MAX(CASE WHEN year = ${years[i]} THEN num_children END) AS y_${i}_children"
            }

            val pivotSql = """
                SELECT province_code, province_name_khmer, $selectAggregates
                FROM nis_21a9_children_under_two
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                GROUP BY province_code, province_name_khmer
                ORDER BY 
                    CAST(NULLIF(regexp_replace(province_code, '\D', '', 'g'), '') AS INT) ASC NULLS LAST,
                    province_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(pivotSql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()
                        row.add(rs.getString("province_code"))
                        row.add(rs.getString("province_name_khmer"))

                        for (i in years.indices) {
                            row.add(rs.getNullableInt("y_${i}_children"))
                        }
                        rows.add(row)
                    }
                }
            }

            val topHeader = mutableListOf<HeaderItem>()
            topHeader.add(HeaderItem("កូដ"))
            topHeader.add(HeaderItem("រាជធានី/ខេត្ត"))

            for (yr in years) {
                topHeader.add(HeaderItem(yr.toString()))
            }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងសង្គមកិច្ច អតីតយុទ្ធជន និងយុវនីតិសម្បទា",
                sub_title = "21A9 ស្ថិតិចំនួនកុមារអាយុក្រោម ២ឆ្នាំ នៅក្នុងគ្រួសារមានបណ្ណសមធម៌ ទទួលបានប្រាក់ឧបត្ថម្ភពីកម្មវិធីជាតិជំនួយសង្គមប្រចាំឆ្នាំ",
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