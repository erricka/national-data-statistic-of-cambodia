package com.kh.niswebportal.app.nis15.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

data class ExportCompanyData(
    val serialNo: String,
    val itemCode: String,
    val companyType: String,
    val yearlyCounts: MutableMap<Int, Double?> = mutableMapOf()
)

@Service
class Nis15A6InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_15a6"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY item_code) as item_order,
                    serial_no,
                    item_code,
                    company_type,
                    year,
                    count_value
                FROM nis_15a6_export_companies
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY item_order ASC, year ASC
            """.trimIndent()

            val companyMap = mutableMapOf<String, ExportCompanyData>()
            val orderedItems = mutableListOf<String>()
            val allYears = mutableSetOf<Int>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val itemCode = rs.getString("item_code")
                        val year = rs.getInt("year")

                        if (!companyMap.containsKey(itemCode)) {
                            orderedItems.add(itemCode)
                            companyMap[itemCode] = ExportCompanyData(
                                serialNo = rs.getString("serial_no") ?: "",
                                itemCode = itemCode,
                                companyType = rs.getString("company_type") ?: ""
                            )
                        }

                        allYears.add(year)
                        companyMap[itemCode]!!.yearlyCounts[year] = rs.getNullableDouble("count_value")
                    }
                }
            }

            val sortedYears = allYears.sorted()

            // 1. Construct Flat Headers
            val headerRow = mutableListOf<HeaderItem>()
            headerRow.add(HeaderItem(title = "ល.រ"))
            headerRow.add(HeaderItem(title = "កូដ"))
            headerRow.add(HeaderItem(title = "រោងចក្រ-ក្រុមហ៊ុន"))

            // Dynamically add Year columns to the header
            for (year in sortedYears) {
                headerRow.add(HeaderItem(title = year.toString()))
            }

            // 2. Construct Pivot Data Rows
            val tbRows = mutableListOf<List<Any?>>()

            for (itemCode in orderedItems) {
                val row = mutableListOf<Any?>()
                val data = companyMap[itemCode]!!

                row.add(data.serialNo)
                row.add(data.itemCode)
                row.add(data.companyType)

                // Append each year's data dynamically
                for (year in sortedYears) {
                    row.add(data.yearlyCounts[year])
                }

                tbRows.add(row)
            }

            return DynamicReportResponse(
                title = "ក្រសួងពាណិជ្ជកម្ម", // Ministry of Commerce
                sub_title = "15A6 ស្ថិតិក្រុមហ៊ុន រោងចក្រ ចុះបញ្ជីនាំចេញក្រោមប្រព័ន្ធអនុគ្រោះពាណិជ្ជកម្មប្រចាំឆ្នាំ", // Export Companies/Factories Stats
                tb_headers = listOf(headerRow),
                tb_rows = tbRows
            )
        }
    }

    
}