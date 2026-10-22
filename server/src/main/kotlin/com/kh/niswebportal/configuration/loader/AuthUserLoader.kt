package com.kh.niswebportal.configuration.loader

import com.kh.niswebportal.app.settings.user.UserEbc
import com.kh.niswebportal.core.com.MData
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component

@Component
class AuthUserLoader(private val userEbc: UserEbc) {

    fun load(userId: String): AuthUser {
        val param = com.kh.niswebportal.core.builder.buildMData { setString("userID", userId) }
        val user  = userEbc.retrieveUserByUserID(param)
        val roles = userEbc.retrieveRolesByUserID(param)
        return build(userId, user, roles)
    }

    fun load(userId: String, user: MData): AuthUser {
        val param = com.kh.niswebportal.core.builder.buildMData { setString("userID", userId) }
        val roles = userEbc.retrieveRolesByUserID(param)
        return build(userId, user, roles)
    }

    fun build(userId: String, user: MData, roles: List<MData>): AuthUser {
        val roleIds = roles.mapNotNull { it.getString("roleId")?.takeIf(String::isNotBlank) }
        return AuthUser(
            userId = userId,
            userType = (user.getString("userType") ?: "").trim().uppercase(),
            displayName = listOfNotNull(user.getString("firstName"), user.getString("lastName"))
                .joinToString(" ").ifBlank { null },
            email = user.getString("email")?.trim(),
            roleIds = roleIds,
            firstRegisterUser = user.getString("firstRegisterUser")?.trim(),
            firstRegisterDate = user.getString("firstRegisterDate")?.trim(),
            lastChangeDate = user.getString("lastChangeDate")?.trim(),
        )
    }

    fun authorities(authUser: AuthUser): List<SimpleGrantedAuthority> {
        val list = mutableListOf(SimpleGrantedAuthority("ROLE_${authUser.userType}"))
        authUser.roleIds.forEach { list.add(SimpleGrantedAuthority("ROLE_$it")) }
        return list
    }
}