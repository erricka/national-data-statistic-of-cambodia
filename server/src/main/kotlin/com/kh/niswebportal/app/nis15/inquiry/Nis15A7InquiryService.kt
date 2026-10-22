package com.kh.niswebportal.app.nis15.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

data class ExportEntityData(
    val serialNo: String,
    val entityName: String,
    val yearlyCounts: MutableMap<Int, Double?> = mutableMapOf()
)

@Service
class Nis15A7InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_15a7"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY entity_name) as entity_order,
                    serial_no,
                    entity_name,
                    year,
                    count_value
                FROM nis_15a7_export_entities
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY entity_order ASC, year ASC
            """.trimIndent()

            val entityMap = mutableMapOf<String, ExportEntityData>()
            val orderedEntities = mutableListOf<String>()
            val allYears = mutableSetOf<Int>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val entityName = rs.getString("entity_name")
                        val year = rs.getInt("year")

                        if (!entityMap.containsKey(entityName)) {
                            orderedEntities.add(entityName)
                            entityMap[entityName] = ExportEntityData(
                                serialNo = rs.getString("serial_no") ?: "",
                                entityName = entityName
                            )
                        }

                        allYears.add(year)
                        entityMap[entityName]!!.yearlyCounts[year] = rs.getNullableDouble("count_value")
                    }
                }
            }

            val sortedYears = allYears.sorted()

            // 1. Construct Flat Headers
            val headerRow = mutableListOf<HeaderItem>()
            headerRow.add(HeaderItem(title = "ល.រ"))
            headerRow.add(HeaderItem(title = "NAME"))

            // Dynamically add Year columns to the header
            for (year in sortedYears) {
                headerRow.add(HeaderItem(title = year.toString()))
            }

            // 2. Construct Pivot Data Rows
            val tbRows = mutableListOf<List<Any?>>()

            for (entityName in orderedEntities) {
                val row = mutableListOf<Any?>()
                val data = entityMap[entityName]!!

                row.add(data.serialNo)
                row.add(data.entityName)

                // Append each year's data dynamically
                for (year in sortedYears) {
                    row.add(data.yearlyCounts[year])
                }

                tbRows.add(row)
            }

            return DynamicReportResponse(
                title = "ក្រសួងពាណិជ្ជកម្ម", // Ministry of Commerce
                sub_title = "15A7 ស្ថិតិនាំចេញក្រោមប្រព័ន្ធអនុគ្រោះពាណិជ្ជកម្មប្រចាំឆ្នាំ", // Export Stats under Preferences
                tb_headers = listOf(headerRow),
                tb_rows = tbRows
            )
        }
    }

    
}