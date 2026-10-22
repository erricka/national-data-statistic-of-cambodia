package com.kh.niswebportal.configuration.lDap

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.ldap.core.LdapTemplate
import org.springframework.ldap.core.support.LdapContextSource

@ConfigurationProperties(prefix = "ldap")
data class LdapProperties(
    var urls: String = "",
    var base: String = "",
    var username: String = "",
    var password: String = "",
    var referral: String = "follow"
)

@Configuration
@EnableConfigurationProperties(LdapProperties::class)
class LdapConfig(
    private val ldapProperties: LdapProperties
) {

    @Bean
    fun contextSource(): LdapContextSource {
        val urlArray = ldapProperties.urls
            .split(",")
            .map { it.trim() }
            .toTypedArray()

        return LdapContextSource().apply {
            urls = urlArray
            setBase(ldapProperties.base)
            userDn = ldapProperties.username
            password = ldapProperties.password
            setReferral(ldapProperties.referral)
            isPooled = true
            afterPropertiesSet()
        }
    }

    @Bean
    fun ldapTemplate(contextSource: LdapContextSource): LdapTemplate {
        return LdapTemplate(contextSource).apply {
            setIgnorePartialResultException(true)
        }
    }
}