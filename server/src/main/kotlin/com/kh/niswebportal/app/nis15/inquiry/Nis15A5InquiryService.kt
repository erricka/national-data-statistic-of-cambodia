package com.kh.niswebportal.app.nis15.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

data class ExportPreferenceData(
    val serialNo: String,
    val coForm: String,
    val categoryDesc: String,
    val yearlyCounts: MutableMap<Int, Double?> = mutableMapOf()
)

@Service
class Nis15A5InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_15a5"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY co_form) as form_order,
                    serial_no,
                    co_form,
                    category_desc,
                    year,
                    count_value
                FROM nis_15a5_export_preferences
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY form_order ASC, year ASC
            """.trimIndent()

            val exportMap = mutableMapOf<String, ExportPreferenceData>()
            val orderedForms = mutableListOf<String>()
            val allYears = mutableSetOf<Int>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val coForm = rs.getString("co_form")
                        val year = rs.getInt("year")

                        if (!exportMap.containsKey(coForm)) {
                            orderedForms.add(coForm)
                            exportMap[coForm] = ExportPreferenceData(
                                serialNo = rs.getString("serial_no") ?: "",
                                coForm = coForm,
                                categoryDesc = rs.getString("category_desc") ?: ""
                            )
                        }

                        allYears.add(year)
                        exportMap[coForm]!!.yearlyCounts[year] = rs.getNullableDouble("count_value")
                    }
                }
            }

            val sortedYears = allYears.sorted()

            // 1. Construct Flat Headers
            val headerRow = mutableListOf<HeaderItem>()
            headerRow.add(HeaderItem(title = "ល.រ"))
            headerRow.add(HeaderItem(title = "ទម្រង់ CO"))
            headerRow.add(HeaderItem(title = "យថាប្រភេទ"))

            // Dynamically add Year columns to the header
            for (year in sortedYears) {
                headerRow.add(HeaderItem(title = year.toString()))
            }

            // 2. Construct Pivot Data Rows
            val tbRows = mutableListOf<List<Any?>>()

            for (coForm in orderedForms) {
                val row = mutableListOf<Any?>()
                val data = exportMap[coForm]!!

                row.add(data.serialNo)
                row.add(data.coForm)
                row.add(data.categoryDesc)

                // Append each year's data dynamically
                for (year in sortedYears) {
                    row.add(data.yearlyCounts[year])
                }

                tbRows.add(row)
            }

            return DynamicReportResponse(
                title = "ក្រសួងពាណិជ្ជកម្ម", // Ministry of Commerce
                sub_title = "15A5 ស្ថិតិនាំចេញក្រោមប្រព័ន្ធអនុគ្រោះពាណិជ្ជកម្មប្រចាំឆ្នាំ", // Export Stats under Preferences
                tb_headers = listOf(headerRow),
                tb_rows = tbRows
            )
        }
    }

    
}