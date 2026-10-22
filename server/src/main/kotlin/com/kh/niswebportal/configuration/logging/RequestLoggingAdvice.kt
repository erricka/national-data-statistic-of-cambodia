package com.kh.niswebportal.configuration.logging

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.kh.niswebportal.core.com.MData
import org.slf4j.LoggerFactory
import org.springframework.core.MethodParameter
import org.springframework.http.HttpInputMessage
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter
import java.lang.reflect.Type

@RestControllerAdvice
class RequestLoggingAdvice : RequestBodyAdviceAdapter() {

    private val log = LoggerFactory.getLogger(RequestLoggingAdvice::class.java)
    private val objectMapper = jacksonObjectMapper()

    override fun supports(methodParameter: MethodParameter, targetType: Type, converterType: Class<out HttpMessageConverter<*>>): Boolean {
        return true
    }

    override fun afterBodyRead(body: Any, inputMessage: HttpInputMessage, parameter: MethodParameter, targetType: Type, converterType: Class<out HttpMessageConverter<*>>): Any {
        val uri = parameter.method?.declaringClass?.let {
            parameter.method?.name
        } ?: "unknown"

        val masked = if (body is MData) {
            val map = body.toMap().toMutableMap()
            if (map.containsKey("password")) map["password"] = "***"
            objectMapper.writeValueAsString(map)
        } else {
            objectMapper.writeValueAsString(body)
        }

        log.info(">>> Request[{}]: {}", inputMessage.headers.contentType, masked)
        return body
    }
}