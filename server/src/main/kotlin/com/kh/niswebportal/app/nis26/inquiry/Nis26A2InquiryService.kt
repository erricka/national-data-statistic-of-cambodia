package com.kh.niswebportal.app.nis26.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

data class CourtOfficialData(
    val proCode: String,
    val provinceName: String,
    val totals: MutableMap<Int, Double?> = mutableMapOf(),
    val admins: MutableMap<Int, Double?> = mutableMapOf(),
    val clerks: MutableMap<Int, Double?> = mutableMapOf(),
    val bailiffs: MutableMap<Int, Double?> = mutableMapOf()
)

@Service
class Nis26A2InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_26a2"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY province_name) as prov_order,
                    pro_code,
                    province_name,
                    year,
                    total_officials,
                    admin_officials,
                    clerks,
                    bailiffs
                FROM nis_26a2_court_officials
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY prov_order ASC, year ASC
            """.trimIndent()

            val provinceMap = mutableMapOf<String, CourtOfficialData>()
            val orderedProvinces = mutableListOf<String>()
            val allYears = mutableSetOf<Int>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val provName = rs.getString("province_name")
                        val year = rs.getInt("year")

                        if (!provinceMap.containsKey(provName)) {
                            orderedProvinces.add(provName)
                            provinceMap[provName] = CourtOfficialData(
                                proCode = rs.getString("pro_code") ?: "",
                                provinceName = provName
                            )
                        }

                        val data = provinceMap[provName]!!

                        allYears.add(year)
                        data.totals[year] = rs.getNullableDouble("total_officials")
                        data.admins[year] = rs.getNullableDouble("admin_officials")
                        data.clerks[year] = rs.getNullableDouble("clerks")
                        data.bailiffs[year] = rs.getNullableDouble("bailiffs")
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

            // Static Sub Headers
            subHeader.add(HeaderItem(title = "ល.រ"))
            subHeader.add(HeaderItem(title = "សាលាជម្រះក្តី"))

            // Dynamically add Year columns
            for (year in sortedYears) {
                topHeader.add(HeaderItem(title = "ឆ្នាំ$year"))
                topHeader.add(HeaderItem(title = "")) // Span
                topHeader.add(HeaderItem(title = "")) // Span
                topHeader.add(HeaderItem(title = "")) // Span

                subHeader.add(HeaderItem(title = "សរុប"))
                subHeader.add(HeaderItem(title = "មន្រ្តីរដ្ឋបាល"))
                subHeader.add(HeaderItem(title = "ក្រឡាបញ្ជី"))
                subHeader.add(HeaderItem(title = "អាជ្ញាសាលា"))
            }

            // 2. Construct Pivot Data Rows
            val tbRows = mutableListOf<List<Any?>>()

            for (provName in orderedProvinces) {
                val data = provinceMap[provName]!!
                val row = mutableListOf<Any?>()

                row.add(data.proCode)
                row.add(data.provinceName)

                // Append each year's data dynamically
                for (y in sortedYears) {
                    row.add(data.totals[y])
                    row.add(data.admins[y])
                    row.add(data.clerks[y])
                    row.add(data.bailiffs[y])
                }

                tbRows.add(row)
            }

            return DynamicReportResponse(
                title = "ក្រសួងយុត្តិធម៌", // Ministry of Justice
                sub_title = "26A2 ចំនួនមន្ត្រី ក្រឡាបញ្ជី និងអាជ្ញាសាលា បែងចែកតាមសាលាជម្រះក្តី និងតាមរាជធានីខេត្ត", // Officials, Clerks & Bailiffs Stats
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = tbRows
            )
        }
    }

    
}