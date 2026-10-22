package com.kh.niswebportal.app.nis26.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

data class CourtVerdictData(
    val caseType: String,
    val itemCode: String,
    val verdicts: MutableMap<Int, Double?> = mutableMapOf(),
    val adjudications: MutableMap<Int, Double?> = mutableMapOf()
)

data class ProvinceGroup(
    val proCode: String,
    val provinceName: String,
    val items: MutableList<CourtVerdictData> = mutableListOf()
)

@Service
class Nis26A9InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_26a9"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY province_name) as prov_order,
                    MIN(id) OVER (PARTITION BY case_type) as type_order,
                    pro_code,
                    province_name,
                    case_type,
                    item_code,
                    year,
                    num_case_verdicts,
                    num_case_adjudicated
                FROM nis_26a9_court_verdicts
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY prov_order ASC, type_order ASC, year ASC
            """.trimIndent()

            val provinceMap = mutableMapOf<String, ProvinceGroup>()
            val orderedProvinces = mutableListOf<String>()
            val allYears = mutableSetOf<Int>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val provName = rs.getString("province_name")
                        val caseType = rs.getString("case_type")
                        val year = rs.getInt("year")

                        if (!provinceMap.containsKey(provName)) {
                            orderedProvinces.add(provName)
                            provinceMap[provName] = ProvinceGroup(
                                proCode = rs.getString("pro_code") ?: "",
                                provinceName = provName
                            )
                        }

                        val provGroup = provinceMap[provName]!!
                        var typeItem = provGroup.items.find { it.caseType == caseType }

                        if (typeItem == null) {
                            typeItem = CourtVerdictData(
                                caseType = caseType,
                                itemCode = rs.getString("item_code") ?: ""
                            )
                            provGroup.items.add(typeItem)
                        }

                        allYears.add(year)
                        typeItem.verdicts[year] = rs.getNullableDouble("num_case_verdicts")
                        typeItem.adjudications[year] = rs.getNullableDouble("num_case_adjudicated")
                    }
                }
            }

            val sortedYears = allYears.sorted()

            // 1. Construct 2-Tier Headers
            val topHeader = mutableListOf<HeaderItem>()
            val subHeader = mutableListOf<HeaderItem>()

            // Static Top Headers (Using \u200B trick for visual spacing)
            topHeader.add(HeaderItem(title = "\u200B")) // Pro_code
            topHeader.add(HeaderItem(title = "\u200B")) // Province Name
            topHeader.add(HeaderItem(title = "\u200B")) // Case Type
            topHeader.add(HeaderItem(title = "\u200B")) // Code

            // Static Sub Headers
            subHeader.add(HeaderItem(title = "ល.រ"))
            subHeader.add(HeaderItem(title = "សាលាដំបូង"))
            subHeader.add(HeaderItem(title = "ប្រភេទសំណុំរឿង"))
            subHeader.add(HeaderItem(title = "CODE"))

            // Dynamically add Year columns
            for (year in sortedYears) {
                topHeader.add(HeaderItem(title = "ឆ្នាំ$year"))
                topHeader.add(HeaderItem(title = "")) // Span

                subHeader.add(HeaderItem(title = "សាលក្រម"))
                subHeader.add(HeaderItem(title = "សាលដីការ"))
            }

            // 2. Construct Pivot Data Rows
            val tbRows = mutableListOf<List<Any?>>()

            for (provName in orderedProvinces) {
                val group = provinceMap[provName]!!
                var isFirstRow = true

                for (item in group.items) {
                    val row = mutableListOf<Any?>()

                    // Only show Province Name and ID on the first row of the group block
                    if (isFirstRow) {
                        row.add(group.proCode)
                        row.add(group.provinceName)
                        isFirstRow = false
                    } else {
                        row.add("")
                        row.add("")
                    }

                    row.add(item.caseType)
                    row.add(item.itemCode)

                    // Append each year's data dynamically
                    for (y in sortedYears) {
                        row.add(item.verdicts[y])
                        row.add(item.adjudications[y])
                    }

                    tbRows.add(row)
                }
            }

            return DynamicReportResponse(
                title = "ក្រសួងយុត្តិធម៌", // Ministry of Justice
                sub_title = "26A9 ស្ថិតិសាលក្រម សាលដីការបស់តុលាការគ្រប់ជាន់ថ្នាក់ប្រចាំឆ្នាំ", // Court Verdicts and Adjudications Stats
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = tbRows
            )
        }
    }

    
}