package com.kh.niswebportal.configuration.loader

import org.springframework.security.core.AuthenticatedPrincipal
import java.io.Serializable

data class AuthUser(
    val userId      : String,
    val userType    : String,
    val displayName : String?,
    val email       : String?,
    val roleIds     : List<String>,
    val firstRegisterUser : String? = null,
    val firstRegisterDate : String? = null,
    val lastChangeDate    : String? = null,
) : AuthenticatedPrincipal, Serializable {

    override fun getName(): String = userId

    val roleString: String? get() = roleIds.joinToString(",").ifBlank { null }

    companion object { private const val serialVersionUID = 1L }
}