package com.kh.niswebportal.app.nis07.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis07A3InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_07a3"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    year,
                    birth_total,
                    birth_female,
                    birth_cert_total,
                    birth_cert_female,
                    khmer_marriage_couples,
                    khmer_marriage_cert_couples,
                    death_total,
                    death_female,
                    death_cert_total,
                    death_cert_female,
                    foreign_khmer_marriage_couples,
                    foreign_birth_cert_count,
                    foreign_marriage_couples,
                    foreign_death_count
                FROM nis_07a3_vital_statistics
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY year ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(rs.getInt("year").toString())
                        row.add(rs.getNullableInt("birth_total") ?: 0)
                        row.add(rs.getNullableInt("birth_female") ?: 0)
                        row.add(rs.getNullableInt("birth_cert_total") ?: 0)
                        row.add(rs.getNullableInt("birth_cert_female") ?: 0)
                        row.add(rs.getNullableInt("khmer_marriage_couples") ?: 0)
                        row.add(rs.getNullableInt("khmer_marriage_cert_couples") ?: 0)
                        row.add(rs.getNullableInt("death_total") ?: 0)
                        row.add(rs.getNullableInt("death_female") ?: 0)
                        row.add(rs.getNullableInt("death_cert_total") ?: 0)
                        row.add(rs.getNullableInt("death_cert_female") ?: 0)
                        row.add(rs.getNullableInt("foreign_khmer_marriage_couples") ?: 0)
                        row.add(rs.getNullableInt("foreign_birth_cert_count") ?: 0)
                        row.add(rs.getNullableInt("foreign_marriage_couples") ?: 0)
                        row.add(rs.getNullableInt("foreign_death_count") ?: 0)

                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ឆ្នាំ"),
                HeaderItem("សំបុត្រកំណើត"),
                HeaderItem(""),
                HeaderItem("បញ្ជាក់កំណើត"),
                HeaderItem(""),
                HeaderItem("អាពាហ៍ពិពាហ៍ ខ្មែរ-ខ្មែរ"),
                HeaderItem("បញ្ជាក់អាពាហ៍ពិពាហ៍ ខ្មែរ-ខ្មែរ"),
                HeaderItem("មរណភាព"),
                HeaderItem(""),
                HeaderItem("បញ្ជាក់មរណភាព"),
                HeaderItem(""),
                HeaderItem("អាពាហ៍ពិពាហ៍ ខ្មែរ-បរទេស"),
                HeaderItem("សំបុត្រកំណើតជនបរទេស"),
                HeaderItem("អាពាហ៍ពិពាហ៍បរទេស"),
                HeaderItem("មរណភាពជនបរទេស")
            )

            val subHeader = listOf(
                HeaderItem(""),
                HeaderItem("សរុប"),
                HeaderItem("ស្រី"),
                HeaderItem("សរុប"),
                HeaderItem("ស្រី"),
                HeaderItem("គូ"),
                HeaderItem("គូ"),
                HeaderItem("សរុប"),
                HeaderItem("ស្រី"),
                HeaderItem("សរុប"),
                HeaderItem("ស្រី"),
                HeaderItem("គូ"),
                HeaderItem("នាក់"),
                HeaderItem("គូ"),
                HeaderItem("នាក់")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងមហាផ្ទៃ",
                sub_title = "07A3 ស្ថិតិនៃការចុះបញ្ជីអត្រានុកូលដ្ឋានប្រចាំឆ្នាំ",
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