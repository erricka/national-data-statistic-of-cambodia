package com.kh.niswebportal.configuration.loader

import org.springframework.security.core.context.SecurityContextHolder

object AuthContext {

    fun current(): AuthUser? =
        SecurityContextHolder.getContext().authentication?.principal as? AuthUser

    fun userId(): String = current()?.userId ?: "system"
    fun roleIds(): List<String> = current()?.roleIds ?: emptyList()
    fun hasRole(roleId: String): Boolean = current()?.roleIds?.contains(roleId) == true
}