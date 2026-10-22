package com.kh.niswebportal.app.nis16.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis16b7InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_16b7"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
            val yearsQuery = """
                SELECT DISTINCT academic_year 
                FROM nis_16b7_bachelor_majors_yearly
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY academic_year ASC
            """.trimIndent()

            val years = mutableListOf<String>()
            conn.prepareStatement(yearsQuery).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        rs.getString(1)?.let { years.add(it) }
                    }
                }
            }

            // ២. ទាញយកទិន្នន័យជំនាញ និងឆ្នាំ
            val dataQuery = """
                m.major_code, m.major_name, m.study_level, y.academic_year, y.student_count
                FROM nis_16b7_bachelor_majors_statistics m
                LEFT JOIN nis_16b7_bachelor_majors_yearly y ON m.major_code = y.major_code
                ${if (hasUploadId) "WHERE m.upload_id = ?" else "WHERE (m.upload_id IS NULL OR m.upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY m.major_code ASC
            """.trimIndent()

            val majorMap = LinkedHashMap<String, MutableMap<String, String?>>()
            val majorNames = LinkedHashMap<String, String>()

            conn.prepareStatement("SELECT $dataQuery").use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val code = rs.getString("major_code")
                        val name = rs.getString("major_name")
                        val year = rs.getString("academic_year")
                        val count = rs.getString("student_count")

                        majorNames[code] = name
                        if (year != null) {
                            majorMap.getOrPut(code) { mutableMapOf() }[year] = count
                        }
                    }
                }
            }

            // ៣. រៀបចំទិន្នន័យជួរដេក (Rows)
            val tbRows = mutableListOf<List<Any?>>()
            var idx = 1

            for ((code, name) in majorNames) {
                val yearData = majorMap[code] ?: emptyMap()
                val row = mutableListOf<Any?>(idx++, name)

                for (yr in years) {
                    row.add(yearData[yr] ?: "-")
                }
                tbRows.add(row)
            }

            // ៤. រៀបចំក្បាលតារាង (Headers) ដោយស្វ័យប្រវត្តិតាមចំនួនឆ្នាំ
            val topHeader = mutableListOf(
                HeaderItem(title = "ល.រ"),
                HeaderItem(title = "ជំនាញសិក្សា")
            )

            for (yr in years) {
                topHeader.add(HeaderItem(title = yr))
            }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល អប់រំ យុវជន និងកីឡា",
                sub_title = "តារាងទី១៩.២. ចំនួននិស្សិតថ្នាក់បរិញ្ញាបត្រ សិក្សាតាមជំនាញ ក្រោមឱវាទក្រសួងអប់រំ យុវជន និងកីឡា",
                tb_headers = listOf(topHeader),
                tb_rows = tbRows
            )
        }
    }
}