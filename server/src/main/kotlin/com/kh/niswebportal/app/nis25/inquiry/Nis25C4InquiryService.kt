package com.kh.niswebportal.app.nis25.inquiry

import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class Nis25C4InquiryService(
    private val dataSource: DataSource
): NisInquiryStrategy {

    override val sourceId: String = "nis_25c4"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    province_code,
                    province_kh,
                    year,
                    inj_dth_total,
                    inj_dth_f,
                    inj_sinj_total,
                    inj_sinj_f,
                    inj_minj_total,
                    inj_minj_f,
                    case_speed,
                    case_nyld,
                    case_nkr,
                    case_utrn,
                    case_alc,
                    case_ovtk,
                    case_drow,
                    case_drug,
                    case_hlth,
                    case_veh,
                    case_rd,
                    case_wthr,
                    case_oth,
                    case_total
                FROM nis_25c4_road_accident_by_province_stats
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY province_code ASC, year ASC
            """.trimIndent()

            val rows = mutableListOf<List<Any?>>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    var index = 1
                    while (rs.next()) {
                        val row = mutableListOf<Any?>()

                        row.add(index++)
                        row.add(rs.getString("province_code"))
                        row.add(rs.getString("province_kh"))
                        row.add(rs.getString("year"))
                        row.add(rs.getObject("inj_dth_total") ?: "-")
                        row.add(rs.getObject("inj_dth_f") ?: "-")
                        row.add(rs.getObject("inj_sinj_total") ?: "-")
                        row.add(rs.getObject("inj_sinj_f") ?: "-")
                        row.add(rs.getObject("inj_minj_total") ?: "-")
                        row.add(rs.getObject("inj_minj_f") ?: "-")
                        row.add(rs.getObject("case_speed") ?: "-")
                        row.add(rs.getObject("case_nyld") ?: "-")
                        row.add(rs.getObject("case_nkr") ?: "-")
                        row.add(rs.getObject("case_utrn") ?: "-")
                        row.add(rs.getObject("case_alc") ?: "-")
                        row.add(rs.getObject("case_ovtk") ?: "-")
                        row.add(rs.getObject("case_drow") ?: "-")
                        row.add(rs.getObject("case_drug") ?: "-")
                        row.add(rs.getObject("case_hlth") ?: "-")
                        row.add(rs.getObject("case_veh") ?: "-")
                        row.add(rs.getObject("case_rd") ?: "-")
                        row.add(rs.getObject("case_wthr") ?: "-")
                        row.add(rs.getObject("case_oth") ?: "-")
                        row.add(rs.getObject("case_total") ?: "-")

                        rows.add(row)
                    }
                }
            }

            val topHeader = listOf(
                HeaderItem("ល.រ"),
                HeaderItem("កូដរាជធានី/ខេត្ត"),
                HeaderItem("រាជធានី/ខេត្ត"),
                HeaderItem("ឆ្នាំ"),
                HeaderItem("អ្នកស្លាប់-ទាំងពីរភេទ"),
                HeaderItem("អ្នកស្លាប់-ភេទស្រី"),
                HeaderItem("របួសធ្ងន់-ទាំងពីរភេទ"),
                HeaderItem("របួសធ្ងន់-ភេទស្រី"),
                HeaderItem("របួសស្រាល-ទាំងពីរភេទ"),
                HeaderItem("របួសស្រាល-ភេទស្រី"),
                HeaderItem("ល្បឿន"),
                HeaderItem("មិនគោរពសិទ្ធិ"),
                HeaderItem("មិនប្រកាន់ស្តាំ"),
                HeaderItem("បត់គ្រោះថ្នាក់"),
                HeaderItem("ស្រវឹង"),
                HeaderItem("ប្រជែងគ្នា"),
                HeaderItem("ងងុយ"),
                HeaderItem("គ្រឿងញៀន"),
                HeaderItem("បញ្ហាសុខភាព"),
                HeaderItem("កត្តាយានយន្ត"),
                HeaderItem("កត្តាផ្លូវថ្នល់"),
                HeaderItem("កត្តាអាកាសធាតុ"),
                HeaderItem("ផ្សេងៗ"),
                HeaderItem("សរុប")
            )

            return DynamicReportResponse(
                title = "តារាងទិន្នន័យរដ្ឋបាល ក្រសួងសាធារណការ និងដឹកជញ្ជូន",
                sub_title = "25C4 គ្រោះថ្នាក់ចរាចរណ៍ផ្លូវគោកតាមរាជធានី ខេត្ត",
                tb_headers = listOf(topHeader),
                tb_rows = rows
            )
        }
    }
}