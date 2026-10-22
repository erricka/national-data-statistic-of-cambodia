package com.kh.niswebportal.app.nis16.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis16b2InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_16b2"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    institution_name,
                    sector,
                    yr_13_14_total, yr_13_14_f,
                    yr_14_15_total, yr_14_15_f,
                    yr_15_16_total, yr_15_16_f,
                    yr_16_17_total, yr_16_17_f,
                    yr_17_18_total, yr_17_18_f,
                    yr_18_19_total, yr_18_19_f,
                    yr_19_20_total, yr_19_20_f,
                    yr_20_21_total, yr_20_21_f,
                    yr_21_22_total, yr_21_22_f,
                    yr_22_23_total, yr_22_23_f,
                    yr_23_24_total, yr_23_24_f
                FROM nis_16b2
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY sector DESC, institution_code ASC
            """.trimIndent()

            val tbRows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var idx = 1
                    while (rs.next()) {
                        val instName = rs.getString("institution_name") ?: ""

                        tbRows.add(
                            listOf(
                                idx++,
                                instName,
                                rs.getString("yr_13_14_total") ?: "-", rs.getString("yr_13_14_f") ?: "-",
                                rs.getString("yr_14_15_total") ?: "-", rs.getString("yr_14_15_f") ?: "-",
                                rs.getString("yr_15_16_total") ?: "-", rs.getString("yr_15_16_f") ?: "-",
                                rs.getString("yr_16_17_total") ?: "-", rs.getString("yr_16_17_f") ?: "-",
                                rs.getString("yr_17_18_total") ?: "-", rs.getString("yr_17_18_f") ?: "-",
                                rs.getString("yr_18_19_total") ?: "-", rs.getString("yr_18_19_f") ?: "-",
                                rs.getString("yr_19_20_total") ?: "-", rs.getString("yr_19_20_f") ?: "-",
                                rs.getString("yr_20_21_total") ?: "-", rs.getString("yr_20_21_f") ?: "-",
                                rs.getString("yr_21_22_total") ?: "-", rs.getString("yr_21_22_f") ?: "-",
                                rs.getString("yr_22_23_total") ?: "-", rs.getString("yr_22_23_f") ?: "-",
                                rs.getString("yr_23_24_total") ?: "-", rs.getString("yr_23_24_f") ?: "-"
                            )
                        )
                    }
                }
            }

            // 2-Tier Headers for Table 16 (Years 2013-14 to 2023-24)
            val topHeader = mutableListOf(
                HeaderItem(title = "\u200B"),
                HeaderItem(title = "\u200B")
            )
            val years = listOf(
                "2013-14", "2014-15", "2015-16", "2016-17", "2017-18",
                "2018-19", "2019-20", "2020-21", "2021-22", "2022-23", "2023-24"
            )
            for (yr in years) {
                topHeader.add(HeaderItem(title = yr))
                topHeader.add(HeaderItem(title = yr))
            }

            val subHeader = mutableListOf(
                HeaderItem(title = "ល.រ"),
                HeaderItem(title = "ឈ្មោះគ្រឹះស្ថាន")
            )
            for (i in 1..11) {
                subHeader.add(HeaderItem(title = "សរុប"))
                subHeader.add(HeaderItem(title = "ស្រី"))
            }

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល អប់រំ យុវជន និងកីឡា",
                sub_title = "តារាងទី១៦. ចំនួនគ្រូបង្រៀនតាមគ្រឹះស្ថានឧត្តមសិក្សារដ្ឋ និងឯកជន ក្រោមឱវាទក្រសួងអប់រំ យុវជន និងកីឡា សម្រាប់ឆ្នាំសិក្សា ២០១៣-១៤ ដល់ ២០២៣-២៤",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = tbRows
            )
        }
    }
}