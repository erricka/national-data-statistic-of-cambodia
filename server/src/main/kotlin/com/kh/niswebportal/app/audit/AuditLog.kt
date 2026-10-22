package com.kh.niswebportal.app.audit

data class AuditLog(
    var sysName: String = "niswebportal",
    var sysDt: String,
    var sysTime: String,
    var srvcId: String?,
    var traceId: String?,
    var stcd: String,
    var financialTxn: String = "N",
    var errorCode: String?,
    var errorMessage: String?,
    var userId: String?,
    var userRole: String?,
    var ip: String?,
    var approver: String?,
    var channel: String = "ERP",
    var requestMethod: String?,
    var requestUri: String?,
    var requestBody: String?,
    var responseBody: String?,
    var httpStatus: Int?,
    var elapsedMs: Long?
)