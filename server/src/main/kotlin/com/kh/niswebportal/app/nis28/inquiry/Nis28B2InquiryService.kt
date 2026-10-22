package com.kh.niswebportal.app.nis28.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis28B2InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_28b2"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()

            val years = mutableListOf<Int>()
            val yrSql = """
                SELECT DISTINCT year 
                FROM nis_28b2_municipal_land_use_plan
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY year ASC
            """.trimIndent()
            
            conn.prepareStatement(yrSql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) years.add(rs.getInt("year"))
                }
            }
            
            if (years.isEmpty()) {
                return DynamicReportResponse(
                    title = "តារាងទិន្នន័យរដ្ឋបាល ស្ថិតិវិស័យរៀបចំដែនដី នគរូបនីយកម្ម និងសំណង់",
                    sub_title = "28B2 ប្លង់គោលប្រើប្រាស់ដីថ្នាក់ក្រុង ស្រុក ខណ្ឌ និងទីប្រជុំជន",
                    tb_headers = emptyList(),
                    tb_rows = emptyList()
                )
            }
            
            val procSelects = years.joinToString(",\n                    ") { y ->
                "MAX(CASE WHEN year = $y THEN in_process END) AS proc_$y"
            }
            val finSelects = years.joinToString(",\n                    ") { y ->
                "MAX(CASE WHEN year = $y THEN completed END) AS fin_$y"
            }
            val aprvSelects = years.joinToString(",\n                    ") { y ->
                "MAX(CASE WHEN year = $y THEN approved END) AS aprv_$y"
            }
            val kmSelects = years.joinToString(",\n                    ") { y ->
                "MAX(CASE WHEN year = $y THEN total_area_km2 END) AS km_$y"
            }
            
            val combinedSelects = listOf(procSelects, finSelects, aprvSelects, kmSelects)
                .filter { it.isNotBlank() }
                .joinToString(",\n                    ")

            val sql = """
                SELECT 
                    province_code,
                    MAX(province_name_kh) as province_name_kh
                    ${if (combinedSelects.isNotEmpty()) ", $combinedSelects" else ""}
                FROM nis_28b2_municipal_land_use_plan
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                GROUP BY province_code
                ORDER BY province_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()
                        row.add(index++)
                        row.add(rs.getString("province_code"))
                        row.add(rs.getString("province_name_kh"))
                        
                        for(y in years) row.add(rs.getNullableDouble("proc_$y"))
                        for(y in years) row.add(rs.getNullableDouble("fin_$y"))
                        for(y in years) row.add(rs.getNullableDouble("aprv_$y"))
                        for(y in years) row.add(rs.getNullableDouble("km_$y"))
                        
                        rows.add(row)
                    }
                }
            }

            val h1 = mutableListOf(HeaderItem("ល.រ"), HeaderItem("Pro_Code"), HeaderItem("រាជធានី/ខេត្ត"))
            val h2 = mutableListOf(HeaderItem(""), HeaderItem(""), HeaderItem(""))
            
            // Top Header
            h1.add(HeaderItem("កំពុងដំណើរការ"))
            for(i in 1 until years.size) h1.add(HeaderItem(""))
            
            h1.add(HeaderItem("បញ្ចប់"))
            for(i in 1 until years.size) h1.add(HeaderItem(""))
            
            h1.add(HeaderItem("អនុម័ត"))
            for(i in 1 until years.size) h1.add(HeaderItem(""))
            
            h1.add(HeaderItem("ទំហំ (គម២)"))
            for(i in 1 until years.size) h1.add(HeaderItem(""))

            // Sub Header just renders the Year for all 4 groups
            for(i in 0 until 4) {
                years.forEach { y -> h2.add(HeaderItem(y.toString())) }
            }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ស្ថិតិវិស័យរៀបចំដែនដី នគរូបនីយកម្ម និងសំណង់",
                sub_title = "28B2 ប្លង់គោលប្រើប្រាស់ដីថ្នាក់ក្រុង ស្រុក ខណ្ឌ និងទីប្រជុំជន",
                tb_headers = listOf(h1, h2),
                tb_rows = rows
            )
        }
    }
}
