package com.kh.niswebportal.app.nis26.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis26A5InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_26a5"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val dataSql = """
                SELECT 
                    section_code,
                    section_name_khmer,
                    generation,
                    description,
                    num_students_total,
                    num_students_female,
                    num_presiding_judge_total,
                    num_presiding_judge_female,
                    num_prosecutor_total,
                    num_prosecutor_female,
                    remarks
                FROM nis_26a5_training_statistics
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY CAST(REGEXP_REPLACE(section_code, '[^0-9]', '', 'g') AS INT) ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(dataSql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val row = mutableListOf<Any?>(
                            rs.getString("section_code") ?: "",
                            rs.getString("section_name_khmer") ?: "",
                            rs.getString("generation") ?: "",
                            rs.getString("description") ?: "",
                            rs.getNullableInt("num_students_total"),
                            rs.getNullableInt("num_students_female"),
                            rs.getNullableInt("num_presiding_judge_total"),
                            rs.getNullableInt("num_presiding_judge_female"),
                            rs.getNullableInt("num_prosecutor_total"),
                            rs.getNullableInt("num_prosecutor_female"),
                            rs.getString("remarks") ?: ""
                        )
                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("26A5_Code"),
                HeaderItem("ឈ្មោះផ្នែក (Khmer Title)"),
                HeaderItem("ជំនាន់"),
                HeaderItem("កាលបរិច្ឆេទ / បរិយាយ"),
                HeaderItem("ចំនួនសិស្ស"),
                HeaderItem(""),
                HeaderItem("ចៅក្រមអាសនៈ"),
                HeaderItem(""),
                HeaderItem("ចៅក្រមអយ្យការ"),
                HeaderItem(""),
                HeaderItem("ផ្សេងៗ")
            )

            val subHeader = listOf(
                HeaderItem(""),
                HeaderItem(""),
                HeaderItem(""),
                HeaderItem(""),
                HeaderItem("សរុប"),
                HeaderItem("ស្ត្រី"),
                HeaderItem("សរុប"),
                HeaderItem("ស្ត្រី"),
                HeaderItem("សរុប"),
                HeaderItem("ស្ត្រី"),
                HeaderItem("")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងយុត្តិធម៌",
                sub_title = "26A5 ស្ថិតិការបណ្តុះបណ្តាលមន្ត្រីយុត្តិធម៌",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = rows
            )
        }
    }

    private fun ResultSet.getNullableInt(columnName: String): Int? {
        val value = this.getInt(columnName)
        return if (this.wasNull()) null else value
    }
}