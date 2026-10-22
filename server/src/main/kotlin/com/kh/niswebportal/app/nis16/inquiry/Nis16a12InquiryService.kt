package com.kh.niswebportal.app.nis16.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis16a12InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_16a12"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    year,
                    gender,
                    kindergarten,
                    primary_school,
                    lower_secondary,
                    upper_secondary,
                    total
                FROM nis_16a12
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
                        val year = rs.getInt("year")
                        val gender = rs.getString("gender")
                        val kg = rs.getString("kindergarten") ?: "-"
                        val primary = rs.getString("primary_school") ?: "-"
                        val lowerSec = rs.getString("lower_secondary") ?: "-"
                        val upperSec = rs.getString("upper_secondary") ?: "-"
                        val total = rs.getString("total") ?: "-"

                        val genderLabel = when (gender) {
                            "BOTH" -> "ទាំងពីរភេទ"
                            "MALE" -> "ប្រុស"
                            "FEMALE" -> "ស្រី"
                            else -> gender
                        }

                        tbRows.add(
                            listOf(
                                idx++,
                                year.toString(),
                                genderLabel,
                                kg,
                                primary,
                                lowerSec,
                                upperSec,
                                total
                            )
                        )
                    }
                }
            }

            // 2-Tier Headers for Table 12
            val topHeader = listOf(
                HeaderItem(title = "\u200B"),
                HeaderItem(title = "\u200B"),
                HeaderItem(title = "\u200B"),
                HeaderItem(title = "មតេយ្យ"),
                HeaderItem(title = "បឋម"),
                HeaderItem(title = "អនុវិទ្យាល័យ"),
                HeaderItem(title = "វិទ្យាល័យ"),
                HeaderItem(title = "សរុប")
            )

            val subHeader = listOf(
                HeaderItem(title = "ល.រ"),
                HeaderItem(title = "ឆ្នាំសិក្សា"),
                HeaderItem(title = "ភេទ"),
                HeaderItem(title = "16a12_kg"),
                HeaderItem(title = "16a12_prisch"),
                HeaderItem(title = "16a12_ls"),
                HeaderItem(title = "16a12_hs"),
                HeaderItem(title = "16a12_total")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល អប់រំ យុវជន និងកីឡា",
                sub_title = "តារាងទី ១២. ចំនួនសិស្សចុះឈ្មោះចូលរៀនតាមកម្រិតសិក្សា ក្នុងប្រព័ន្ធអប់រំចំណេះទូទៅ ឆ្នាំសិក្សា 1998 ដល់ 2024",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = tbRows
            )
        }
    }
}