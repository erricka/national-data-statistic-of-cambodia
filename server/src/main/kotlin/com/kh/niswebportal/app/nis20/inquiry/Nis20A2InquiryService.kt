package com.kh.niswebportal.app.nis20.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

data class WellPondData(
    val basicWaterPct: Double?,
    val cleanWaterFamilies: Int?,
    val climateWells: Int?,
    val communityPonds: Int?,
    val rainTank4m3: Int?,
    val rainTank35m3: Int?,
    val pipeSystems: Int?,
    val drinkingStations: Int?,
    val filterTanks: Int?,
    val deepWells: Int?,
    val mixedWells: Int?,
    val ponds: Int?
)

@Service
class Nis20A2InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_20a2"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY province_name) as prov_order,
                    province_name,
                    year,
                    basic_water_pct,
                    clean_water_families,
                    climate_wells,
                    community_ponds,
                    rain_tank_4m3,
                    rain_tank_35m3,
                    pipe_systems,
                    drinking_stations,
                    filter_tanks,
                    deep_wells,
                    mixed_wells,
                    ponds
                FROM nis_20a2_wells_ponds
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY prov_order ASC, year ASC
            """.trimIndent()

            val provMap = mutableMapOf<String, MutableMap<Int, WellPondData>>()
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

                        provMap[prov]!![year] = WellPondData(
                            basicWaterPct = rs.getNullableDouble("basic_water_pct"),
                            cleanWaterFamilies = rs.getNullableInt("clean_water_families"),
                            climateWells = rs.getNullableInt("climate_wells"),
                            communityPonds = rs.getNullableInt("community_ponds"),
                            rainTank4m3 = rs.getNullableInt("rain_tank_4m3"),
                            rainTank35m3 = rs.getNullableInt("rain_tank_35m3"),
                            pipeSystems = rs.getNullableInt("pipe_systems"),
                            drinkingStations = rs.getNullableInt("drinking_stations"),
                            filterTanks = rs.getNullableInt("filter_tanks"),
                            deepWells = rs.getNullableInt("deep_wells"),
                            mixedWells = rs.getNullableInt("mixed_wells"),
                            ponds = rs.getNullableInt("ponds")
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
            topHeader.add(HeaderItem(title = "\u200B")) // Basic Water %
            topHeader.add(HeaderItem(title = "\u200B")) // Clean Water Families
            topHeader.add(HeaderItem(title = "\u200B")) // Climate Wells
            topHeader.add(HeaderItem(title = "សាងសង់អាងត្រង់ទឹកភ្លៀង")) // Parent: Rainwater Tanks
            topHeader.add(HeaderItem(title = ""))       // Span
            topHeader.add(HeaderItem(title = ""))       // Span
            topHeader.add(HeaderItem(title = "\u200B")) // Pipe Systems
            topHeader.add(HeaderItem(title = "\u200B")) // Drinking Stations
            topHeader.add(HeaderItem(title = "\u200B")) // Filter Tanks

            // Static Sub Headers
            subHeader.add(HeaderItem(title = "ល.រ"))
            subHeader.add(HeaderItem(title = "ខេត្ត ក្រុង"))
            subHeader.add(HeaderItem(title = "ភាគរយទឹកមូលដ្ឋាន"))
            subHeader.add(HeaderItem(title = "គ្រួសារប្រើប្រាស់ទឹកស្អាត"))
            subHeader.add(HeaderItem(title = "សាងសង់អណ្តូងធន់នឹងការប្រែប្រួលអាកាសធាតុ"))
            subHeader.add(HeaderItem(title = "សាងសង់/ស្តារស្រះសហគមន៍"))
            subHeader.add(HeaderItem(title = "អាងត្រង់ទឹកភ្លៀងចំណុះ ៤ម៣"))
            subHeader.add(HeaderItem(title = "អាងត្រង់ទឹកភ្លៀងចំណុះ ៣៥ម៣"))
            subHeader.add(HeaderItem(title = "សាងសង់ប្រព័ន្ធចែកចាយទឹកតាមបណ្តាញបំពង់ខ្នាតតូចសហគមន៍"))
            subHeader.add(HeaderItem(title = "សាងសង់ស្ថានីយ៍ទឹកផឹកសហគមន៍"))
            subHeader.add(HeaderItem(title = "សាងសង់ធុងចម្រោះទឹកស្អាត"))

            // Dynamic Yearly Headers
            for (year in sortedYears) {
                topHeader.add(HeaderItem(title = "ឆ្នាំ$year"))
                topHeader.add(HeaderItem(title = "")) // Span
                topHeader.add(HeaderItem(title = "")) // Span

                subHeader.add(HeaderItem(title = "អណ្តូងខួងជ្រៅ (ចំនួន)"))
                subHeader.add(HeaderItem(title = "អណ្តូងចម្រុះ(ចំនួន)"))
                subHeader.add(HeaderItem(title = "ស្រះ (ចំនួន)"))
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
                row.add(totals?.basicWaterPct)
                row.add(totals?.cleanWaterFamilies)
                row.add(totals?.climateWells)
                row.add(totals?.communityPonds)
                row.add(totals?.rainTank4m3)
                row.add(totals?.rainTank35m3)
                row.add(totals?.pipeSystems)
                row.add(totals?.drinkingStations)
                row.add(totals?.filterTanks)

                // Append each year dynamically
                for (year in sortedYears) {
                    val yearData = provData[year]
                    row.add(yearData?.deepWells)
                    row.add(yearData?.mixedWells)
                    row.add(yearData?.ponds)
                }

                tbRows.add(row)
            }

            return DynamicReportResponse(
                title = "ក្រសួងអភិវឌ្ឍន៍ជនបទ", // Ministry of Rural Development
                sub_title = "20A2 ស្ថិតិអណ្តូង និងស្រះ", // Statistics of Wells and Ponds
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