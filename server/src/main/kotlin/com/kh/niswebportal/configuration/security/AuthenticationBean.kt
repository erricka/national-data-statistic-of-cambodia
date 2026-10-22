package com.kh.niswebportal.configuration.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.session.SessionInformationExpiredStrategy
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
class AuthenticationBean(
    @Value("\${cors.allowed-origins}") private val allowedOrigins: String
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(configuration: AuthenticationConfiguration): AuthenticationManager =
        configuration.authenticationManager
//    @Bean
//    fun corsConfigurationSource(): CorsConfigurationSource {
//        val origins = allowedOrigins.split(",").map { it.trim() }
//        val config = CorsConfiguration().apply {
//            this.allowedOrigins = origins
//            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
//            allowedHeaders = listOf("*")
//            exposedHeaders = listOf("Set-Cookie")
//            allowCredentials = true
//            maxAge = 3600
//        }
//        return UrlBasedCorsConfigurationSource().apply {
//            registerCorsConfiguration("/**", config)
//        }
//    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val origins = allowedOrigins.split(",").map { it.trim() }
        val config = CorsConfiguration().apply {
            // Use allowedOriginPatterns instead of allowedOrigins
            this.allowedOriginPatterns = origins
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
            allowedHeaders = listOf("*")
            exposedHeaders = listOf("Set-Cookie")
            allowCredentials = true
            maxAge = 3600
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }

    @Bean
    fun filterChain(
        http: HttpSecurity,
        sessionRegistry: SessionRegistry,
        sessionInformationExpiredStrategy: SessionInformationExpiredStrategy,
        customAuthProvider: CustomAuthProvider
    ): SecurityFilterChain {

        http
            .cors { }
            .csrf { it.disable() }
            .authenticationProvider(customAuthProvider)
            .logout { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers("/login").permitAll()
                it.requestMatchers("/logout").permitAll()
                it.requestMatchers("/auth/init").permitAll()
                it.requestMatchers("/actuator/**").permitAll()
                it.anyRequest().permitAll()
            }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
        http.sessionManagement { session ->
            session
                .maximumSessions(1)
                .sessionRegistry(sessionRegistry)
                .expiredSessionStrategy(sessionInformationExpiredStrategy)
        }

        return http.build()
    }
}
