package com.kh.niswebportal.app.nis28.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis28A3InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_28a3"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val years = mutableListOf<Int>()
            val yearSql = """
                SELECT DISTINCT year 
                FROM nis_28a3_sporadic_land_registration
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY year ASC
            """.trimIndent()

            conn.prepareStatement(yearSql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) years.add(rs.getInt("year"))
                }
            }

            if (years.isEmpty()) {
                return DynamicReportResponse(
                    title = "តារាងទិន្នន័យរដ្ឋបាល ស្ថិតិវិស័យរៀបចំដែនដី នគរូបនីយកម្ម និងសំណង់",
                    sub_title = "28A3 ការចុះបញ្ជីដីមានលក្ខណៈដាច់ដោយដុំ",
                    tb_headers = emptyList(),
                    tb_rows = emptyList()
                )
            }

            val selectAggregates = years.indices.joinToString(", ") { i ->
                """
                MAX(CASE WHEN year = ${years[i]} THEN total_titles END) AS t_$i,
                MAX(CASE WHEN year = ${years[i]} THEN total_households END) AS h_$i,
                MAX(CASE WHEN year = ${years[i]} THEN total_hectares END) AS a_$i
                """.trimIndent()
            }

            val pivotSql = """
                SELECT province_code, province_name_kh, unit, $selectAggregates
                FROM nis_28a3_sporadic_land_registration
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                GROUP BY province_code, province_name_kh, unit
                ORDER BY province_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(pivotSql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()
                        row.add(index++)
                        row.add(rs.getString("province_code"))
                        row.add(rs.getString("province_name_kh"))
                        row.add(rs.getString("unit"))

                        for (i in years.indices) {
                            row.add(rs.getNullableDouble("t_$i"))
                            row.add(rs.getNullableDouble("h_$i"))
                            row.add(rs.getNullableDouble("a_$i"))
                        }
                        rows.add(row)
                    }
                }
            }

            val topHeader = mutableListOf<HeaderItem>(
                HeaderItem("ល.រ"), HeaderItem("កូដ"), HeaderItem("រាជធានី/ខេត្ត"), HeaderItem("ខ្នាត")
            )
            val subHeader = mutableListOf<HeaderItem>(
                HeaderItem(""), HeaderItem(""), HeaderItem(""), HeaderItem("")
            )

            for (year in years) {
                topHeader.addAll(listOf(HeaderItem("ឆ្នាំ$year"), HeaderItem(""), HeaderItem("")))
                subHeader.add(HeaderItem("ចំនួនបណ្ណ"))
                subHeader.add(HeaderItem("ចំនួនគ្រួសារ"))
                subHeader.add(HeaderItem("ទំហំ (ហិកតា)"))
            }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ស្ថិតិវិស័យរៀបចំដែនដី នគរូបនីយកម្ម និងសំណង់",
                sub_title = "28A3 ការចុះបញ្ជីដីមានលក្ខណៈដាច់ដោយដុំ",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = rows
            )
        }
    }

    
}