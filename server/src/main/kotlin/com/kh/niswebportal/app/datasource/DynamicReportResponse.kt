package com.kh.niswebportal.app.datasource

data class HeaderItem(
    val title: String
)

data class DynamicReportResponse(
    val tb_headers: List<List<HeaderItem>>,
    val tb_rows: List<List<Any?>>
)