package com.kh.niswebportal.app.nis21.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis21A6InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_21a6"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val years = mutableListOf<Int>()
            val yearSql = """
                SELECT year 
                FROM nis_21a6_veterans_dependents
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
                    sub_title = "21A6 ស្ថិតិចំនួនអតីតយុទ្ធជន និងអ្នកនៅក្នុងបន្ទុកប្រចាំឆ្នាំ",
                    tb_headers = emptyList(),
                    tb_rows = emptyList()
                )
            }

            val selectAggregates = years.indices.joinToString(", ") { i ->
                """
                    MAX(CASE WHEN year = ${years[i]} THEN num_retirees END) AS y_${i}_retirees,
                    MAX(CASE WHEN year = ${years[i]} THEN num_loss_capacity END) AS y_${i}_loss_capacity,
                    MAX(CASE WHEN year = ${years[i]} THEN num_disabled END) AS y_${i}_disabled,
                    MAX(CASE WHEN year = ${years[i]} THEN num_martyrs END) AS y_${i}_martyrs,
                    MAX(CASE WHEN year = ${years[i]} THEN num_deceased END) AS y_${i}_deceased,
                    MAX(CASE WHEN year = ${years[i]} THEN num_dependents END) AS y_${i}_dependents
                """.trimIndent()
            }

            val pivotSql = """
                SELECT province_code, province_name_khmer, $selectAggregates
                FROM nis_21a6_veterans_dependents
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
                            row.add(rs.getNullableInt("y_${i}_retirees"))
                            row.add(rs.getNullableInt("y_${i}_loss_capacity"))
                            row.add(rs.getNullableInt("y_${i}_disabled"))
                            row.add(rs.getNullableInt("y_${i}_martyrs"))
                            row.add(rs.getNullableInt("y_${i}_deceased"))
                            row.add(rs.getNullableInt("y_${i}_dependents"))
                        }
                        rows.add(row)
                    }
                }
            }

            val topHeader = mutableListOf<HeaderItem>()
            val subHeader = mutableListOf<HeaderItem>()

            topHeader.add(HeaderItem("កូដ"))
            topHeader.add(HeaderItem("រាជធានី/ខេត្ត"))
            subHeader.add(HeaderItem("កូដ"))
            subHeader.add(HeaderItem("រាជធានី/ខេត្ត"))

            val subCategories = listOf("និវត្តជន", "បាត់បង់សមត្ថភាព", "ជនពិការ", "ជនពលី", "ជនមរណៈ", "ក្នុងបន្ទុក")

            for (yr in years) {
                subCategories.forEach { cat ->
                    topHeader.add(HeaderItem(yr.toString()))
                    subHeader.add(HeaderItem(cat))
                }
            }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងសង្គមកិច្ច អតីតយុទ្ធជន និងយុវនីតិសម្បទា",
                sub_title = "21A6 ស្ថិតិចំនួនអតីតយុទ្ធជន និងអ្នកនៅក្នុងបន្ទុកប្រចាំឆ្នាំ",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = rows
            )
        }
    }

    private fun ResultSet.getNullableInt(columnName: String): Int? {
        val value = this.getInt(columnName)
        return if (this.wasNull()) null else value
    }
}