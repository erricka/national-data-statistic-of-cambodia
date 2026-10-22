package com.kh.niswebportal.configuration.sessions

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.servlet.http.HttpServletResponse
import com.kh.niswebportal.core.message.CommonMessageCode
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy
import org.springframework.security.web.session.SessionInformationExpiredEvent
import org.springframework.security.web.session.SessionInformationExpiredStrategy

@Configuration
class SessionStrategyConfig {

    @Bean
    fun sessionAuthenticationStrategy(sessionRegistry: SessionRegistry): SessionAuthenticationStrategy {
        val concurrent = ConcurrentSessionControlAuthenticationStrategy(sessionRegistry).apply {
            setMaximumSessions(1)
            setExceptionIfMaximumExceeded(false)
        }
        val register = RegisterSessionAuthenticationStrategy(sessionRegistry)
        return CompositeSessionAuthenticationStrategy(listOf(concurrent, register))
    }

    @Bean
    fun sessionInformationExpiredStrategy(): SessionInformationExpiredStrategy {
        return CustomSessionExpiredStrategy(jacksonObjectMapper())
    }
}

class CustomSessionExpiredStrategy(
    private val objectMapper: ObjectMapper
) : SessionInformationExpiredStrategy {
    override fun onExpiredSessionDetected(event: SessionInformationExpiredEvent) {
        val response = event.response
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = "application/json;charset=UTF-8"
        val body = CommonMessageCode.SOMEONE_LOOGGED_IN.generateIMResponse(
            messages = setOf("Someone has logged in")
        )
        response.writer.write(objectMapper.writeValueAsString(body))
        response.writer.flush()
    }
}