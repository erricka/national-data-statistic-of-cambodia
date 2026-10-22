package com.kh.niswebportal.configuration.sessions

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.core.session.SessionRegistryImpl
import org.springframework.security.web.session.HttpSessionEventPublisher

@Configuration
class SessionBeans {
    @Bean
    fun sessionRegistry(): SessionRegistry = SessionRegistryImpl()
    @Bean fun httpSessionEventPublisher() = HttpSessionEventPublisher()

}


