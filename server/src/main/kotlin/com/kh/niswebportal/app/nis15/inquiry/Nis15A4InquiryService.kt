package com.kh.niswebportal.app.nis15.inquiry

import com.kh.niswebportal.core.util.getNullableDouble
import com.kh.niswebportal.app.datasource.HeaderItem
import com.kh.niswebportal.core.strategy.DynamicReportResponse
import com.kh.niswebportal.core.strategy.NisInquiryStrategy
import org.springframework.stereotype.Service
import java.sql.ResultSet
import javax.sql.DataSource

data class CertificateData(
    val itemCode: String,
    val serialNo: String,
    val coForm: String,
    val agreementScheme: String,
    val yearlyCounts: MutableMap<Int, Double?> = mutableMapOf()
)

@Service
class Nis15A4InquiryService(
    private val dataSource: DataSource
) : NisInquiryStrategy {

    override val sourceId: String = "nis_15a4"

    override fun getDynamicReport(uploadId: String?): DynamicReportResponse {
        dataSource.connection.use { conn ->
            val hasUploadId = !uploadId.isNullOrBlank()
val sql = """
                SELECT 
                    MIN(id) OVER (PARTITION BY item_code) as item_order,
                    item_code,
                    serial_no,
                    co_form,
                    agreement_scheme,
                    year,
                    count_value
                FROM nis_15a4_certificate_of_origin
                ${if (hasUploadId) "WHERE upload_id = ?" else "WHERE (upload_id IS NULL OR upload_id IN (SELECT upload_id FROM datasource_history WHERE status = '01'))"}
                ORDER BY item_order ASC, year ASC
            """.trimIndent()

            val certificateMap = mutableMapOf<String, CertificateData>()
            val orderedItems = mutableListOf<String>()
            val allYears = mutableSetOf<Int>()

            conn.prepareStatement(sql).use { stmt ->
                if (hasUploadId) stmt.setString(1, uploadId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val itemCode = rs.getString("item_code")
                        val year = rs.getInt("year")

                        if (!certificateMap.containsKey(itemCode)) {
                            orderedItems.add(itemCode)
                            certificateMap[itemCode] = CertificateData(
                                itemCode = itemCode,
                                serialNo = rs.getString("serial_no") ?: "",
                                coForm = rs.getString("co_form") ?: "",
                                agreementScheme = rs.getString("agreement_scheme") ?: ""
                            )
                        }

                        allYears.add(year)
                        certificateMap[itemCode]!!.yearlyCounts[year] = rs.getNullableDouble("count_value")
                    }
                }
            }

            val sortedYears = allYears.sorted()

            // 1. Construct Flat Headers
            val headerRow = mutableListOf<HeaderItem>()
            headerRow.add(HeaderItem(title = "No."))               // Serial No
            headerRow.add(HeaderItem(title = "CO Form"))           // CO Form Code
            headerRow.add(HeaderItem(title = "Agreement/Scheme"))  // Agreement Description

            // Dynamically add Year columns to the header
            for (year in sortedYears) {
                headerRow.add(HeaderItem(title = year.toString()))
            }

            // 2. Construct Pivot Data Rows
            val tbRows = mutableListOf<List<Any?>>()

            for (itemCode in orderedItems) {
                val row = mutableListOf<Any?>()
                val data = certificateMap[itemCode]!!

                row.add(data.serialNo)
                row.add(data.coForm)
                row.add(data.agreementScheme)

                // Append each year's data dynamically
                for (year in sortedYears) {
                    row.add(data.yearlyCounts[year])
                }

                tbRows.add(row)
            }

            return DynamicReportResponse(
                title = "ក្រសួងពាណិជ្ជកម្ម", // Ministry of Commerce
                sub_title = "15A4 ស្ថិតិចេញវិញ្ញាបនបត្របញ្ជាក់ដើមកំណើតទំនិញប្រចាំឆ្នាំ", // Certificate of Origin Statistics
                tb_headers = listOf(headerRow),
                tb_rows = tbRows
            )
        }
    }

    
}