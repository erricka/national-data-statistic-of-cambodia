package com.kh.niswebportal.app.nis90.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class B1a2InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "b1a2"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()

            // 1. Get Distinct Data Dates
            val dataDates = mutableListOf<String>()
            val dateSql = """
                SELECT DISTINCT data_date 
                FROM public.nis_90_stats
                WHERE source_id = ? AND
                (${if (hasUploadId) "upload_id = ?" else "(upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"})
                ORDER BY data_date ASC
            """.trimIndent()

            conn.prepareStatement(dateSql).use { stmt ->
                stmt.setString(1, sourceId)
                if (hasUploadId) stmt.setString(2, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        dataDates.add(rs.getString("data_date"))
                    }
                }
            }

            // 2. Pivot
            val pivotSelects = dataDates.joinToString(",\n") { dt ->
                "MAX(CASE WHEN data_date = '$dt' THEN metric_value END) as \"$dt\""
            }

            val sql = """
                SELECT 
                    indicator_code,
                    MAX(unit) as unit
                    ${if (dataDates.isNotEmpty()) ", $pivotSelects" else ""}
                FROM public.nis_90_stats
                WHERE source_id = ? AND
                (${if (hasUploadId) "upload_id = ?" else "(upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"})
                GROUP BY indicator_code
                ORDER BY indicator_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, sourceId)
                if (hasUploadId) stmt.setString(2, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()
                        row.add(index++)
                        row.add(rs.getString("indicator_code"))
                        
                        // We'll see if the dataset has units. The query returns MAX(unit)
                        var un = rs.getString("unit")
                        if (!un.isNullOrBlank()) row.add(un)
                        
                        for (dt in dataDates) {
                            row.add(rs.getString(dt) ?: "-")
                        }
                        rows.add(row)
                    }
                }
            }

            val topHeader = mutableListOf<HeaderItem>()
            topHeader.add(HeaderItem("ល.រ"))
            topHeader.add(HeaderItem("Product Code"))
            
            // Checking if unit exists in results (heuristic based on rows)
            var showUnit = false
            if (rows.isNotEmpty() && rows[0].size > dataDates.size + 2) {
                topHeader.add(HeaderItem("Unit"))
                showUnit = true
            }

            dataDates.forEach { dt -> topHeader.add(HeaderItem(dt.replace("_", " "))) }

            return DynamicReportResponse(
                title = "Export Data (b1a2)",
                sub_title = "Data Source: b1a2",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }
}
