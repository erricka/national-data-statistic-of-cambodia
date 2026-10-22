package com.kh.niswebportal.core.strategy

import com.kh.niswebportal.app.datasource.HeaderItem
import org.springframework.web.multipart.MultipartFile

/**
 * Standard interface for file migration/import processing
 */
interface NisMigratorStrategy {
    /** The source ID identifier matching your category/table name, e.g., "nis_26a1" */
    val sourceId: String
    fun processAndMigrate(file: MultipartFile, uploadId: String): Int
}

/**
 * Standard interface for dynamic inquiry/preview reports
 */
interface NisInquiryStrategy {
    val sourceId: String
    fun getDynamicReport(uploadId: String? = null): DynamicReportResponse
}

data class DynamicReportResponse(
    val title: String? = null,
    val sub_title: String? = null,
    val tb_headers: List<List<HeaderItem>>,
    val tb_rows: List<List<Any?>>
)