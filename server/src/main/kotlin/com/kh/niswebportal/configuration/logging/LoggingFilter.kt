package com.kh.niswebportal.configuration.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class LoggingFilter : OncePerRequestFilter() {

    companion object {
        const val IM_LOG_HEADER = "im-log"
        const val IM_LOG_MDC = "im_log"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            MDC.put(IM_LOG_MDC, request.getHeader(IM_LOG_HEADER))
            MDC.put("op", request.requestURI.replace("/", ""))
            filterChain.doFilter(request, response)
        } finally {
            MDC.clear()
        }
    }
}