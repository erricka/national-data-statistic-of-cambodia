package com.kh.niswebportal.app.nis07.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.sql.ResultSet
import javax.sql.DataSource

@Service
class Nis07A11InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_07a11"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    pro_code, provinces_name, accident_current_year, accident_prev_year, accident_growth, accident_percent,
                    death_current_year, death_prev_year, death_growth, death_percent,
                    injury_current_year, injury_prev_year, injury_growth, injury_percent,
                    heavy_injury_current_year, heavy_injury_prev_year, heavy_injury_growth, heavy_injury_percent,
                    light_injury_current_year, light_injury_prev_year, light_injury_growth, light_injury_percent
                FROM nis_07a11_traffic_accidents
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY id,pro_code ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()
                        row.add(rs.getString("pro_code"))
                        row.add(rs.getString("provinces_name"))

                        row.add(rs.getNullableInt("accident_current_year") ?: 0)
                        row.add(rs.getNullableInt("accident_prev_year") ?: 0)
                        row.add(rs.getNullableInt("accident_growth") ?: 0)
                        row.add(rs.getBigDecimal("accident_percent") ?: BigDecimal.ZERO)

                        row.add(rs.getNullableInt("death_current_year") ?: 0)
                        row.add(rs.getNullableInt("death_prev_year") ?: 0)
                        row.add(rs.getNullableInt("death_growth") ?: 0)
                        row.add(rs.getBigDecimal("death_percent") ?: BigDecimal.ZERO)

                        row.add(rs.getNullableInt("injury_current_year") ?: 0)
                        row.add(rs.getNullableInt("injury_prev_year") ?: 0)
                        row.add(rs.getNullableInt("injury_growth") ?: 0)
                        row.add(rs.getBigDecimal("injury_percent") ?: BigDecimal.ZERO)

                        row.add(rs.getNullableInt("heavy_injury_current_year") ?: 0)
                        row.add(rs.getNullableInt("heavy_injury_prev_year") ?: 0)
                        row.add(rs.getNullableInt("heavy_injury_growth") ?: 0)
                        row.add(rs.getBigDecimal("heavy_injury_percent") ?: BigDecimal.ZERO)

                        row.add(rs.getNullableInt("light_injury_current_year") ?: 0)
                        row.add(rs.getNullableInt("light_injury_prev_year") ?: 0)
                        row.add(rs.getNullableInt("light_injury_growth") ?: 0)
                        row.add(rs.getBigDecimal("light_injury_percent") ?: BigDecimal.ZERO)

                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"), HeaderItem("រាជធានី ខេត្ត"),
                HeaderItem("គ្រោះថ្នាក់"), HeaderItem(""), HeaderItem(""), HeaderItem(""),
                HeaderItem("ស្លាប់"), HeaderItem(""), HeaderItem(""), HeaderItem(""),
                HeaderItem("របួស"), HeaderItem(""), HeaderItem(""), HeaderItem(""),
                HeaderItem("របួសធ្ងន់"), HeaderItem(""), HeaderItem(""), HeaderItem(""),
                HeaderItem("របួសស្រាល"), HeaderItem(""), HeaderItem(""), HeaderItem("")
            )

            val subHeader = listOf(
                HeaderItem(""), HeaderItem(""),
                HeaderItem("ឆ្នាំ២០២៤"), HeaderItem("ឆ្នាំ២០២៣"), HeaderItem("កើន/ថយ"), HeaderItem("%"),
                HeaderItem("ឆ្នាំ២០២៤"), HeaderItem("ឆ្នាំ២០២៣"), HeaderItem("កើន/ថយ"), HeaderItem("%"),
                HeaderItem("ឆ្នាំ២០២៤"), HeaderItem("ឆ្នាំ២០២៣"), HeaderItem("កើន/ថយ"), HeaderItem("%"),
                HeaderItem("ឆ្នាំ២០២៤"), HeaderItem("ឆ្នាំ២០២៣"), HeaderItem("កើន/ថយ"), HeaderItem("%"),
                HeaderItem("ឆ្នាំ២០២៤"), HeaderItem("ឆ្នាំ២០២៣"), HeaderItem("កើន/ថយ"), HeaderItem("%")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងមហាផ្ទៃ",
                sub_title = "07A11 ស្ថិតិគ្រោះថ្នាក់ចរាចរណ៍ និងចំនួនអ្នកស្លាប់ប្រចាំឆ្នាំ",
                tb_headers = listOf(topHeader, subHeader),
                tb_rows = rows
            )
        }
    }

    private fun ResultSet.getNullableInt(col: String): Int? {
        val v = this.getInt(col)
        return if (this.wasNull()) null else v
    }
}