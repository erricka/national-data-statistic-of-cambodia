package com.kh.niswebportal.app.nis16.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis16a14InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_16a14"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    year,
                    gender,
                    prisch_prom,
                    prisch_rep,
                    prisch_drop,
                    ls_prom,
                    ls_rep,
                    ls_drop,
                    us_prom,
                    us_rep,
                    us_drop,
                    canps_lv6,
                    canps_lv9,
                    canps_lv12
                FROM nis_16a14
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY 
                    CASE gender 
                        WHEN 'BOTH' THEN 1 
                        WHEN 'MALE' THEN 2 
                        WHEN 'FEMALE' THEN 3 
                        ELSE 4 
                    END, 
                    year ASC
            """.trimIndent()

            val tbRows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var idx = 1
                    while (rs.next()) {
                        val year = rs.getString("year")
                        val gender = rs.getString("gender")
                        val prischProm = rs.getString("prisch_prom") ?: "-"
                        val prischRep = rs.getString("prisch_rep") ?: "-"
                        val prischDrop = rs.getString("prisch_drop") ?: "-"
                        val lsProm = rs.getString("ls_prom") ?: "-"
                        val lsRep = rs.getString("ls_rep") ?: "-"
                        val lsDrop = rs.getString("ls_drop") ?: "-"
                        val usProm = rs.getString("us_prom") ?: "-"
                        val usRep = rs.getString("us_rep") ?: "-"
                        val usDrop = rs.getString("us_drop") ?: "-"
                        val canpsLv6 = rs.getString("canps_lv6") ?: "-"
                        val canpsLv9 = rs.getString("canps_lv9") ?: "-"
                        val canpsLv12 = rs.getString("canps_lv12") ?: "-"

                        val genderLabel = when (gender) {
                            "BOTH" -> "ទាំងពីរភេទ"
                            "MALE" -> "ប្រុស"
                            "FEMALE" -> "ស្រី"
                            else -> gender
                        }

                        tbRows.add(
                            listOf(
                                idx++,
                                year,
                                genderLabel,
                                prischProm,
                                prischRep,
                                prischDrop,
                                lsProm,
                                lsRep,
                                lsDrop,
                                usProm,
                                usRep,
                                usDrop,
                                canpsLv6,
                                canpsLv9,
                                canpsLv12
                            )
                        )
                    }
                }
            }

            // 2-Tier Headers matching Table 14 columns
            val topHeader = listOf(
                HeaderItem(title = "\u200B"),
                HeaderItem(title = "\u200B"),
                HeaderItem(title = "\u200B"),
                HeaderItem(title = "បឋមសិក្សា"),
                HeaderItem(title = "បឋមសិក្សា"),
                HeaderItem(title = "បឋមសិក្សា"),
                HeaderItem(title = "មធ្យមសិក្សាបឋមភូមិ"),
                HeaderItem(title = "មធ្យមសិក្សាបឋមភូមិ"),
                HeaderItem(title = "មធ្យមសិក្សាបឋមភូមិ"),
                HeaderItem(title = "មធ្យមសិក្សាទុតិយភូមិ"),
                HeaderItem(title = "មធ្យមសិក្សាទុតិយភូមិ"),
                HeaderItem(title = "មធ្យមសិក្សាទុតិយភូមិ"),
                HeaderItem(title = "បេក្ខជនដែលប្រឡងជាប់"),
                HeaderItem(title = "បេក្ខជនដែលប្រឡងជាប់"),
                HeaderItem(title = "បេក្ខជនដែលប្រឡងជាប់")
            )

            val subHeader = listOf(
                HeaderItem(title = "ល.រ"),
                HeaderItem(title = "ឆ្នាំសិក្សា"),
                HeaderItem(title = "ភេទ"),
                HeaderItem(title = "ឡើងថ្នាក់"),
                HeaderItem(title = "ត្រួតថ្នាក់"),
                HeaderItem(title = "បោះបង់"),
                HeaderItem(title = "ឡើងថ្នាក់"),
                HeaderItem(title = "ត្រួតថ្នាក់"),
                HeaderItem(title = "បោះបង់"),
                HeaderItem(title = "ឡើងថ្នាក់"),
                HeaderItem(title = "ត្រួតថ្នាក់"),
                HeaderItem(title = "បោះបង់"),
                HeaderItem(title = "ថ្នាក់ទី៦"),
                HeaderItem(title = "ថ្នាក់ទី៩"),
                HeaderItem(title = "ថ្នាក់ទី១២")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល អប់រំ យុវជន និងកីឡា",
                sub_title = "តារាងទី១៤. អត្រាលំហូរសិស្សតាមកម្រិតសិក្សា និងសិស្សប្រលងជាប់ ឆ្នាំសិក្សា១៩៩៨ ដល់២០២៤",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = tbRows
            )
        }
    }
}