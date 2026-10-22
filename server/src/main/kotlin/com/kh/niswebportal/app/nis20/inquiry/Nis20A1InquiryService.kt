package com.kh.niswebportal.app.nis20.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

data class RuralRoadData(
    val totalLength: Double?,
    val totalLines: Int?,
    val dbstLength: Double?,
    val concreteLength: Double?,
    val gravelLength: Double?,
    val bridges: Int?,
    val boxCulverts: Int?,
    val pipeCulverts: Int?
)

@Service
class Nis20A1InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_20a1"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY province_name) as prov_order,
                    province_name,
                    year,
                    total_length,
                    total_lines,
                    dbst_length,
                    concrete_length,
                    gravel_length,
                    bridges,
                    box_culverts,
                    pipe_culverts
                FROM nis_20a1_rural_roads
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY prov_order ASC, year ASC
            """.trimIndent()

            val provMap = mutableMapOf<String, MutableMap<Int, RuralRoadData>>()
            val orderedProvs = mutableListOf<String>()
            val allYears = mutableSetOf<Int>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val prov = rs.getString("province_name")
                        val year = rs.getInt("year")

                        if (!provMap.containsKey(prov)) {
                            orderedProvs.add(prov)
                            provMap[prov] = mutableMapOf()
                        }

                        if (year > 0) allYears.add(year)

                        provMap[prov]!![year] = RuralRoadData(
                            totalLength = rs.getNullableDouble("total_length"),
                            totalLines = rs.getNullableInt("total_lines"),
                            dbstLength = rs.getNullableDouble("dbst_length"),
                            concreteLength = rs.getNullableDouble("concrete_length"),
                            gravelLength = rs.getNullableDouble("gravel_length"),
                            bridges = rs.getNullableInt("bridges"),
                            boxCulverts = rs.getNullableInt("box_culverts"),
                            pipeCulverts = rs.getNullableInt("pipe_culverts")
                        )
                    }
                }
            }

            val sortedYears = allYears.sorted()

            // 1. Construct 2-Tier Headers
            val topHeader = mutableListOf<HeaderItem>()
            val subHeader = mutableListOf<HeaderItem>()

            // Static Top Headers (Using \u200B trick to align spans)
            topHeader.add(HeaderItem(title = "\u200B")) // No.
            topHeader.add(HeaderItem(title = "\u200B")) // Province
            topHeader.add(HeaderItem(title = "\u200B")) // Total Length
            topHeader.add(HeaderItem(title = "\u200B")) // Total Lines
            topHeader.add(HeaderItem(title = "ប្រភេទកម្រាល")) // Road Types
            topHeader.add(HeaderItem(title = ""))       // Span
            topHeader.add(HeaderItem(title = ""))       // Span
            topHeader.add(HeaderItem(title = "សំណង់សិល្បការ")) // Structures
            topHeader.add(HeaderItem(title = ""))       // Span
            topHeader.add(HeaderItem(title = ""))       // Span

            // Static Sub Headers
            subHeader.add(HeaderItem(title = "ល.រ"))
            subHeader.add(HeaderItem(title = "ខេត្ត ក្រុង"))
            subHeader.add(HeaderItem(title = "ប្រវែងផ្លូវសរុប (គម)"))
            subHeader.add(HeaderItem(title = "ចំនួនខ្សែផ្លូវ (ខ្សែ)"))
            subHeader.add(HeaderItem(title = "ផ្លូវក្រាលកៅស៊ូ DBST(គម)"))
            subHeader.add(HeaderItem(title = "ផ្លូវបេតុង(គម)"))
            subHeader.add(HeaderItem(title = "ផ្លូវល្បាយគ្រួស(គម)"))
            subHeader.add(HeaderItem(title = "ស្ពាន (ចំនួន)"))
            subHeader.add(HeaderItem(title = "លូប្រអប់ (ចំនួន)"))
            subHeader.add(HeaderItem(title = "លូមូល (ចំនួន)"))

            // Dynamic Yearly Headers
            for (year in sortedYears) {
                topHeader.add(HeaderItem(title = "ឆ្នាំ$year"))
                topHeader.add(HeaderItem(title = "")) // Span
                topHeader.add(HeaderItem(title = "")) // Span

                subHeader.add(HeaderItem(title = "ផ្លូវក្រាលកៅស៊ូ DBST(គម)"))
                subHeader.add(HeaderItem(title = "ផ្លូវបេតុង(គម)"))
                subHeader.add(HeaderItem(title = "ផ្លូវល្បាយគ្រួស(គម)"))
            }

            // 2. Construct Pivot Data Rows
            val tbRows = mutableListOf<List<Any?>>()
            var serial = 1

            for (prov in orderedProvs) {
                val row = mutableListOf<Any?>()
                val provData = provMap[prov]!!
                val totals = provData[0] // Year 0 represents the summary block

                row.add(serial++)
                row.add(prov)
                row.add(totals?.totalLength)
                row.add(totals?.totalLines)
                row.add(totals?.dbstLength)
                row.add(totals?.concreteLength)
                row.add(totals?.gravelLength)
                row.add(totals?.bridges)
                row.add(totals?.boxCulverts)
                row.add(totals?.pipeCulverts)

                // Append each year dynamically
                for (year in sortedYears) {
                    val yearData = provData[year]
                    row.add(yearData?.dbstLength)
                    row.add(yearData?.concreteLength)
                    row.add(yearData?.gravelLength)
                }

                tbRows.add(row)
            }

            return DynamicReportResponse(
                title = "ក្រសួងអភិវឌ្ឍន៍ជនបទ", // Ministry of Rural Development
                sub_title = "20A1 ស្ថិតិផ្លូវជនបទ", // Rural Road Statistics
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = tbRows
            )
        }
    }

    private fun ResultSet.getNullableInt(columnName: String): Int? {
        val value = this.getInt(columnName)
        return if (this.wasNull()) null else value
    }

    
}