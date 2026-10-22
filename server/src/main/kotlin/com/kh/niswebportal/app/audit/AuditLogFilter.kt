package com.kh.niswebportal.app.audit

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import com.kh.niswebportal.configuration.loader.AuthUser
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.core.context.SecurityContext
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class AuditLogFilter(private val auditLogService: AuditLogService) : OncePerRequestFilter() {

    companion object {
        private const val AUDIT_PATH_PREFIX = "/api/"
        private const val MAX_BODY_CHARS = 8000
        private const val SEC_CONTEXT_KEY = "SPRING_SECURITY_CONTEXT"
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val TIME_FMT = DateTimeFormatter.ofPattern("HHmmssSSS")
        private val SUCCESS_CODES = setOf("000000", "0000", "00000", "200", "SUCCESS", "S", "OK")
        private val SENSITIVE_KEYS = setOf("password","newpassword", "currentpassword", "passwd", "secret", "token", "pin", "otp" )
    }

    private val objectMapper = jacksonObjectMapper()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (request.method.equals("OPTIONS", ignoreCase = true)) return true
        return !request.requestURI.startsWith(AUDIT_PATH_PREFIX)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val wrappedRequest = ContentCachingRequestWrapper(request, MAX_BODY_CHARS)
        val wrappedResponse = ContentCachingResponseWrapper(response)
        val start = System.currentTimeMillis()

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse)
        } finally {
            val elapsed = System.currentTimeMillis() - start
            try {
                auditLogService.record(buildAuditLog(wrappedRequest, wrappedResponse, elapsed))
            } catch (_: Exception) {
            }
            wrappedResponse.copyBodyToResponse()
        }
    }

    private fun buildAuditLog(
        request: ContentCachingRequestWrapper,
        response: ContentCachingResponseWrapper,
        elapsed: Long
    ): AuditLog {
        val now = LocalDateTime.now()
        val sysDt = now.format(DATE_FMT)
        val sysTime = now.format(TIME_FMT)

        val reqBytes = request.contentAsByteArray
        val resBytes = response.contentAsByteArray
        val responseNode = parse(resBytes)

        val status = resolveStatus(response.status, responseNode)
        val principal = resolvePrincipal(request)

        val traceId = request.getHeader("trace-id")?.takeIf { it.isNotBlank() }
            ?: generateTraceId(sysDt, sysTime)

        return AuditLog(
            sysDt = sysDt,
            sysTime = sysTime,
            srvcId = lastSegment(request.requestURI),
            traceId = traceId,
            stcd = status.stcd,
            errorCode = status.errorCode,
            errorMessage = status.errorMessage,
            userId = principal?.userId,
            userRole = principal?.roleString,
            ip = resolveIp(request),
            approver = null,
            requestMethod = request.method,
            requestUri = request.requestURI,
            requestBody = requestBodyJson(reqBytes),
            responseBody = responseBodyJson(resBytes, responseNode),
            httpStatus = response.status,
            elapsedMs = elapsed
        )
    }

    private data class Status(val stcd: String, val errorCode: String?, val errorMessage: String?)

    private fun resolveStatus(httpStatus: Int, body: JsonNode?): Status {
        val code = body?.let { firstText(it, "code", "rspCd", "resultCode", "msgCd") }
        val message = body?.let { firstText(it, "message", "rspMsg", "resultMessage", "msg") }
        val httpOk = httpStatus in 200..299
        val codeOk = code == null || SUCCESS_CODES.contains(code.uppercase())
        if (httpOk && codeOk) return Status("Y", null, null)
        return Status("N", code ?: httpStatus.toString(), message)
    }

    private fun resolvePrincipal(request: HttpServletRequest): AuthUser? {
        val session = request.getSession(false) ?: return null
        val ctx = session.getAttribute(SEC_CONTEXT_KEY) as? SecurityContext ?: return null
        val auth = ctx.authentication ?: return null
        if (!auth.isAuthenticated || auth.name == "anonymousUser") return null
        return auth.principal as? AuthUser
    }

    private fun resolveIp(request: HttpServletRequest): String? {
        val forwarded = request.getHeader("X-Forwarded-For")
        val ip = if (!forwarded.isNullOrBlank()) forwarded.split(",").first().trim() else request.remoteAddr
        return ip?.take(15)
    }

    private fun lastSegment(uri: String): String? =
        uri.trimEnd('/').substringAfterLast('/').ifBlank { null }

    private fun generateTraceId(sysDt: String, sysTime: String): String {
        val rand = (100000..999999).random()
        return "NIS$sysDt$sysTime$rand"
    }

    private fun parse(bytes: ByteArray): JsonNode? {
        if (bytes.isEmpty()) return null
        return try { objectMapper.readTree(bytes) } catch (_: Exception) { null }
    }

    private fun firstText(node: JsonNode, vararg keys: String): String? {
        for (k in keys) {
            val v = node.get(k)
            if (v != null && !v.isNull && (v.isTextual || v.isNumber || v.isBoolean)) return v.asText()
        }
        return null
    }

    private fun maskNode(node: JsonNode) {
        if (node.isObject) {
            val obj = node as ObjectNode
            for (k in obj.fieldNames().asSequence().toList()) {
                if (SENSITIVE_KEYS.contains(k.lowercase())) obj.put(k, "***")
                else maskNode(obj.get(k))
            }
        } else if (node.isArray) {
            node.forEach { maskNode(it) }
        }
    }

    private fun truncated(size: Int): String = """{"_truncated":true,"size":$size}"""

    private fun requestBodyJson(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val text = String(bytes, Charsets.UTF_8)
        val node = parse(bytes)
            ?: return if (text.length > MAX_BODY_CHARS) truncated(text.length)
            else objectMapper.writeValueAsString(mapOf("_raw" to text))
        maskNode(node)
        val masked = objectMapper.writeValueAsString(node)
        return if (masked.length > MAX_BODY_CHARS) truncated(masked.length) else masked
    }

    private fun responseBodyJson(bytes: ByteArray, node: JsonNode?): String? {
        if (bytes.isEmpty()) return null
        val text = String(bytes, Charsets.UTF_8)
        if (text.length > MAX_BODY_CHARS) return truncated(text.length)
        return if (node != null) text else objectMapper.writeValueAsString(mapOf("_raw" to text))
    }
}