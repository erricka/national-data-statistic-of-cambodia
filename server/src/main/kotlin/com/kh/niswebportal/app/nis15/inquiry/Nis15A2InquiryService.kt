package com.kh.niswebportal.app.nis15.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

data class BranchData(
    val serialNo: String,
    val yearlyCounts: MutableMap<Int, Double?> = mutableMapOf()
)

@Service
class Nis15A2InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_15a2"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY registration_type) as type_order,
                    serial_no,
                    registration_type,
                    year,
                    count_value
                FROM nis_15a2_branch_registration
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY type_order ASC, year ASC
            """.trimIndent()

            val registrationMap = mutableMapOf<String, BranchData>()
            val orderedTypes = mutableListOf<String>()
            val allYears = mutableSetOf<Int>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val type = rs.getString("registration_type")
                        val year = rs.getInt("year")

                        if (!registrationMap.containsKey(type)) {
                            orderedTypes.add(type)
                            registrationMap[type] = BranchData(
                                serialNo = rs.getString("serial_no") ?: ""
                            )
                        }

                        allYears.add(year)
                        registrationMap[type]!!.yearlyCounts[year] = rs.getNullableDouble("count_value")
                    }
                }
            }

            val sortedYears = allYears.sorted()

            // 1. Construct Flat Headers
            val headerRow = mutableListOf<HeaderItem>()
            headerRow.add(HeaderItem(title = "ល.រ"))
            headerRow.add(HeaderItem(title = "ប្រភេទការចុះបញ្ជីម៉ាក"))

            // Dynamically add Year columns to the header
            for (year in sortedYears) {
                headerRow.add(HeaderItem(title = year.toString()))
            }

            // 2. Construct Pivot Data Rows
            val tbRows = mutableListOf<List<Any?>>()

            for (type in orderedTypes) {
                val row = mutableListOf<Any?>()
                val data = registrationMap[type]!!

                row.add(data.serialNo)
                row.add(type)

                // Append each year's data dynamically
                for (year in sortedYears) {
                    row.add(data.yearlyCounts[year])
                }

                tbRows.add(row)
            }

            return DynamicReportResponse(
                title = "ក្រសួងពាណិជ្ជកម្ម", // Ministry of Commerce
                sub_title = "15A2 ស្ថិតិសាខាក្រុមហ៊ុនបរទេស និងសាខាក្រុមហ៊ុនក្នុងស្រុកប្រចាំឆ្នាំ", // Foreign & Domestic Company Branches
                tb_headers = listOf(headerRow),
                tb_rows = tbRows
            )
        }
    }

    
}