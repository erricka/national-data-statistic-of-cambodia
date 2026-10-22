package com.kh.niswebportal.app.nis20.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

data class SanitationData(
    val basicSanitationPct: Double?,
    val odfStatus: Double?,
    val odfPlusStatus: Double?,
    val familiesUsingLatrines: Int?,
    val latrinesBuilt: Int?,
    val publicLatrinesBuilt: Int?,
    val allTypeLatrines: Int?,
    val ecoLatrines: Int?
)

@Service
class Nis20A3InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_20a3"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY province_name) as prov_order,
                    province_name,
                    year,
                    basic_sanitation_pct,
                    odf_status,
                    odf_plus_status,
                    families_using_latrines,
                    latrines_built,
                    public_latrines_built,
                    all_type_latrines,
                    eco_latrines
                FROM nis_20a3_sanitation
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY prov_order ASC, year ASC
            """.trimIndent()

            val provMap = mutableMapOf<String, MutableMap<Int, SanitationData>>()
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

                        provMap[prov]!![year] = SanitationData(
                            basicSanitationPct = rs.getNullableDouble("basic_sanitation_pct"),
                            odfStatus = rs.getNullableDouble("odf_status"),
                            odfPlusStatus = rs.getNullableDouble("odf_plus_status"),
                            familiesUsingLatrines = rs.getNullableInt("families_using_latrines"),
                            latrinesBuilt = rs.getNullableInt("latrines_built"),
                            publicLatrinesBuilt = rs.getNullableInt("public_latrines_built"),
                            allTypeLatrines = rs.getNullableInt("all_type_latrines"),
                            ecoLatrines = rs.getNullableInt("eco_latrines")
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
            topHeader.add(HeaderItem(title = "\u200B")) // Basic Sanitation Pct
            topHeader.add(HeaderItem(title = "\u200B")) // ODF
            topHeader.add(HeaderItem(title = "\u200B")) // ODF+
            topHeader.add(HeaderItem(title = "\u200B")) // Families using Latrines
            topHeader.add(HeaderItem(title = "\u200B")) // Built Latrines
            topHeader.add(HeaderItem(title = "\u200B")) // Public Latrines

            // Static Sub Headers
            subHeader.add(HeaderItem(title = "ល.រ"))
            subHeader.add(HeaderItem(title = "ខេត្ត ក្រុង"))
            subHeader.add(HeaderItem(title = "ភាគរយអនាម័យមូលដ្ឋាន (%)"))
            subHeader.add(HeaderItem(title = "រាជធានី ខេត្តឈប់បន្ទោបង់ពាសវាពាសកាល (ODF)"))
            subHeader.add(HeaderItem(title = "រាជធានី ខេត្តឈប់បន្ទោបង់ពាសវាពាសកាលកម្រិតខ្ពស់(ODF+)"))
            subHeader.add(HeaderItem(title = "ចំនួនគ្រួសារប្រើប្រាស់បង្គន់អនាម័យ"))
            subHeader.add(HeaderItem(title = "សាងសង់បង្គន់អនាម័យ"))
            subHeader.add(HeaderItem(title = "សាងសង់បង្គន់អនាម័យសាធារណៈ"))

            // Dynamic Yearly Headers
            for (year in sortedYears) {
                topHeader.add(HeaderItem(title = "ឆ្នាំ$year"))
                topHeader.add(HeaderItem(title = "")) // Span

                subHeader.add(HeaderItem(title = "បង្គន់អនាម័យគ្រប់ប្រភេទ (ចំនួន)"))
                subHeader.add(HeaderItem(title = "បង្គន់អនាម័យដែលមានបរិស្ថានល្អ នៅតំបន់ប្រឈម (ចំនួនបង្គន់)"))
            }

            // 2. Construct Pivot Data Rows
            val tbRows = mutableListOf<List<Any?>>()
            var serial = 1

            for (prov in orderedProvs) {
                val row = mutableListOf<Any?>()
                val provData = provMap[prov]!!
                val totals = provData[0] // Year 0 represents the static summary block

                row.add(serial++)
                row.add(prov)
                row.add(totals?.basicSanitationPct)
                row.add(totals?.odfStatus)
                row.add(totals?.odfPlusStatus)
                row.add(totals?.familiesUsingLatrines)
                row.add(totals?.latrinesBuilt)
                row.add(totals?.publicLatrinesBuilt)

                // Append each year dynamically
                for (year in sortedYears) {
                    val yearData = provData[year]
                    row.add(yearData?.allTypeLatrines)
                    row.add(yearData?.ecoLatrines)
                }

                tbRows.add(row)
            }

            return DynamicReportResponse(
                title = "ក្រសួងអភិវឌ្ឍន៍ជនបទ", // Ministry of Rural Development
                sub_title = "20A3 ស្ថិតិចំនួនបង្គន់អនាម័យ", // Sanitation & Latrine Statistics
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