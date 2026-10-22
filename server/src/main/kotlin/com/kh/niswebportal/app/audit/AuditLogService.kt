package com.kh.niswebportal.app.audit

import jakarta.annotation.PreDestroy
import com.kh.niswebportal.core.builder.buildMData
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.Executors

@Service
class AuditLogService(private val auditLogEbc: AuditLogEbc) {

    private val log = LoggerFactory.getLogger(AuditLogService::class.java)
    private val executor = Executors.newVirtualThreadPerTaskExecutor()

    fun record(auditLog: AuditLog) {
        val param = auditLog.toMData()
        executor.execute {
            try {
//                auditLogEbc.registerAuditLog(param)
            } catch (e: Exception) {
                log.warn("Failed to write audit log: {}", e.message)
            }
        }
    }

    @PreDestroy
    fun shutdown() = executor.close()

    private fun AuditLog.toMData() = com.kh.niswebportal.core.builder.buildMData {
        val actor = userId ?: "system"
        setString("sysName", sysName)
        setString("sysDt", sysDt)
        setString("sysTime", sysTime)
        setString("srvcId", srvcId)
        setString("traceId", traceId)
        setString("stcd", stcd)
        setString("financialTxn", financialTxn)
        setString("errorCode", errorCode)
        setString("errorMessage", errorMessage)
        setString("userId", userId)
        setString("userRole", userRole)
        setString("ip", ip)
        setString("approver", approver)
        setString("channel", channel)
        setString("requestMethod", requestMethod)
        setString("requestUri", requestUri)
        setString("requestBody", requestBody)
        setString("responseBody", responseBody)
        set("httpStatus", httpStatus)
        set("elapsedMs", elapsedMs)
        setString("firstRegisterUser", actor)
        setString("firstRegisterDate", sysDt)
        setString("firstRegisterTime", sysTime)
        setString("lastChangeUser", actor)
        setString("lastChangeDate", sysDt)
        setString("lastChangeTime", sysTime)
    }
}