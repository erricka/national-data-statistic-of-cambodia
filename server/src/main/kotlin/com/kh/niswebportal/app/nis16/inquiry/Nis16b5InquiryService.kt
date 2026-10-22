package com.kh.niswebportal.app.nis16.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis16b5InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_16b5"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
            val yearsQuery = """
                SELECT DISTINCT academic_year 
                FROM nis_16b5_bachelor_students_yearly
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

            // ២. ទាញយកទិន្នន័យគ្រឹះស្ថាន និងឆ្នាំ
            val dataQuery = """
                i.institution_code, i.institution_name, i.sector, y.academic_year, y.total_count, y.female_count
                FROM nis_16b5_bachelor_students_statistics i
                LEFT JOIN nis_16b5_bachelor_students_yearly y ON i.institution_code = y.institution_code
                ${if (hasUploadId) "WHERE i.upload_id = ?" else "WHERE (i.upload_id IS NULL OR i.upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY i.sector DESC, i.institution_code ASC
            """.trimIndent()

            val institutionMap = LinkedHashMap<String, MutableMap<String, Pair<String?, String?>>>()
            val institutionNames = LinkedHashMap<String, String>()

            conn.prepareStatement("SELECT $dataQuery").use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val code = rs.getString("institution_code")
                        val name = rs.getString("institution_name")
                        val year = rs.getString("academic_year")
                        val total = rs.getString("total_count")
                        val female = rs.getString("female_count")

                        institutionNames[code] = name
                        if (year != null) {
                            institutionMap.getOrPut(code) { mutableMapOf() }[year] = Pair(total, female)
                        }
                    }
                }
            }

            // ៣. រៀបចំទិន្នន័យជួរដេក (Rows)
            val tbRows = mutableListOf<List<Any?>>()
            var idx = 1

            for ((code, name) in institutionNames) {
                val yearData = institutionMap[code] ?: emptyMap()
                val row = mutableListOf<Any?>(idx++, name)

                for (yr in years) {
                    val stats = yearData[yr]
                    row.add(stats?.first ?: "-")  // សរុប
                    row.add(stats?.second ?: "-") // ស្រី
                }
                tbRows.add(row)
            }

            // ៤. រៀបចំក្បាលតារាង ២ ជាន់ (Headers) ដោយស្វ័យប្រវត្តិតាមចំនួនឆ្នាំ
            val topHeader = mutableListOf(
                HeaderItem(title = "\u200B"),
                HeaderItem(title = "\u200B")
            )
            val subHeader = mutableListOf(
                HeaderItem(title = "ល.រ"),
                HeaderItem(title = "ឈ្មោះគ្រឹះស្ថាន")
            )

            for (yr in years) {
                topHeader.add(HeaderItem(title = yr))
                topHeader.add(HeaderItem(title = yr))
                subHeader.add(HeaderItem(title = "សរុប"))
                subHeader.add(HeaderItem(title = "ស្រី"))
            }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល អប់រំ យុវជន និងកីឡា",
                sub_title = "តារាងទី១៨.២. ចំនួននិស្សិតថ្នាក់បរិញ្ញាបត្រ តាមគ្រឹះស្ថានឧត្តមសិក្សារដ្ឋ និងឯកជន ក្រោមឱវាទក្រសួងអប់រំ យុវជន និងកីឡា",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = tbRows
            )
        }
    }
}