package com.kh.niswebportal.app.nis16.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis16b1InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_16b1"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    year,
                    gender,
                    pub_tch,
                    pub_stf,
                    pub_stu,
                    pri_tch,
                    pri_stf,
                    pri_stu,
                    pri_avg_tuit
                FROM nis_16b1
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
                        val pubTch = rs.getString("pub_tch") ?: "-"
                        val pubStf = rs.getString("pub_stf") ?: "-"
                        val pubStu = rs.getString("pub_stu") ?: "-"
                        val priTch = rs.getString("pri_tch") ?: "-"
                        val priStf = rs.getString("pri_stf") ?: "-"
                        val priStu = rs.getString("pri_stu") ?: "-"
                        val priAvgTuit = rs.getString("pri_avg_tuit") ?: "-"

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
                                pubTch,
                                pubStf,
                                pubStu,
                                priTch,
                                priStf,
                                priStu,
                                priAvgTuit
                            )
                        )
                    }
                }
            }

            // 2-Tier Headers matching Table 15 columns
            val topHeader = listOf(
                HeaderItem(title = "\u200B"),
                HeaderItem(title = "\u200B"),
                HeaderItem(title = "\u200B"),
                HeaderItem(title = "រដ្ឋ"),
                HeaderItem(title = "រដ្ឋ"),
                HeaderItem(title = "រដ្ឋ"),
                HeaderItem(title = "ឯកជន"),
                HeaderItem(title = "ឯកជន"),
                HeaderItem(title = "ឯកជន"),
                HeaderItem(title = "ឯកជន")
            )

            val subHeader = listOf(
                HeaderItem(title = "ល.រ"),
                HeaderItem(title = "ឆ្នាំសិក្សា"),
                HeaderItem(title = "ភេទ"),
                HeaderItem(title = "ចំនួន គ្រូបង្រៀន"),
                HeaderItem(title = "ចំនួនបុគ្គលិក គាំទ្រ"),
                HeaderItem(title = "ចំនួន និស្សិត"),
                HeaderItem(title = "ចំនួន គ្រូបង្រៀន"),
                HeaderItem(title = "ចំនួនបុគ្គលិក គាំទ្រ"),
                HeaderItem(title = "ចំនួន និស្សិត"),
                HeaderItem(title = "តម្លៃសិក្សា មធ្យម/ឆ្នាំ (ដុល្លារ)")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល អប់រំ យុវជន និងកីឡា",
                sub_title = "តារាងទី១៥. ចំនួនគ្រូបង្រៀន បុគ្គលិកគាំទ្រ និងនិស្សិត ក្រោមឱវាទក្រសួងអប់រំ យុវជន និងកីឡា សម្រាប់ឆ្នាំសិក្សា ២០១៣-១៤ ដល់ ២០២៣-២៤",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = tbRows
            )
        }
    }
}