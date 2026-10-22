package com.kh.niswebportal.app.nis32.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis32A1InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_32a1"

    override fun getDynamicReport(uploadId: String? ): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    year,
                    category_code,
                    category_name_kh,
                    unit,
                    import_domestic,
                    export_domestic,
                    import_international,
                    export_international,
                    total_amount
                FROM nis_32a1_air_cargo_transport
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY year ASC, category_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(index++)
                        row.add(rs.getInt("year"))
                        row.add(rs.getString("category_code"))
                        row.add(rs.getString("category_name_kh"))
                        row.add(rs.getString("unit"))
                        row.add(rs.getNullableDouble("import_domestic") ?: 0.0)
                        row.add(rs.getNullableDouble("export_domestic") ?: 0.0)
                        row.add(rs.getNullableDouble("import_international") ?: 0.0)
                        row.add(rs.getNullableDouble("export_international") ?: 0.0)
                        row.add(rs.getNullableDouble("total_amount") ?: 0.0)

                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("ឆ្នាំ"),
                HeaderItem("កូដ"),
                HeaderItem("ប្រភេទ"),
                HeaderItem("ខ្នាត"),
                HeaderItem("នាំចូល (ក្នុងស្រុក)"),
                HeaderItem("នាំចេញ (ក្នុងស្រុក)"),
                HeaderItem("នាំចូល (អន្តរជាតិ)"),
                HeaderItem("នាំចេញ (អន្តរជាតិ)"),
                HeaderItem("សរុប")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល លេខាធិការដ្ឋានអាកាសចរណ៍ស៊ីវិល",
                sub_title = "32A1 ស្ថិតិដឹកជញ្ជូនតាមផ្លូវអាកាស(ទំនិញ)ប្រចាំខែ ត្រីមាស និងប្រចាំឆ្នាំ",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }

    
}