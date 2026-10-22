package com.kh.niswebportal.configuration.security

import com.kh.niswebportal.app.settings.user.UserEbc
import com.kh.niswebportal.configuration.lDap.LdapUserService
import com.kh.niswebportal.configuration.loader.AuthUserLoader
import com.kh.niswebportal.core.com.MData
import org.springframework.core.env.Environment
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class CustomAuthProvider(
    private val userEbc: UserEbc,
    private val ldapUserService: LdapUserService,
    private val passwordEncoder: PasswordEncoder,
    private val env: Environment,
    private val authUserLoader: AuthUserLoader
) : AuthenticationProvider {

    override fun authenticate(authentication: Authentication): Authentication {
        val username = (authentication.name ?: "").trim()
        val password = (authentication.credentials?.toString() ?: "")
        if (username.isBlank() || password.isBlank()) {
            throw BadCredentialsException("Missing username or password")
        }
        val param = com.kh.niswebportal.core.builder.buildMData { setString("userID", username) }
        val user = userEbc.retrieveUserByUserID(param)
        val userType = (user.getString("userType") ?: "").trim().uppercase()
        return when (userType) {
            "ADMIN", "SYSUSER" -> authenticateBcrypt(user, username, password, userType)
            "APPUSER" -> authenticateLdap(username, password)
            else -> throw BadCredentialsException("Invalid user type")
        }
    }

    private fun authenticateBcrypt(
        user: MData,
        username: String,
        password: String,
        userType: String
    ): Authentication {
        val activeProfile = env.activeProfiles.firstOrNull() ?: "default"
        val raw = "$activeProfile|$username|$password"
        val bcryptHash = user.getString("password") ?: throw BadCredentialsException("No password hash")
        if (!passwordEncoder.matches(raw, bcryptHash)) {
            throw BadCredentialsException("Invalid credentials")
        }
        val authUser = authUserLoader.load(username, user)
        return UsernamePasswordAuthenticationToken(authUser, null, authUserLoader.authorities(authUser))
    }

    private fun authenticateLdap(username: String, password: String): Authentication {
        if (!ldapUserService.authenticate(username, password)) {
            throw BadCredentialsException("LDAP authentication failed")
        }
        val authUser = authUserLoader.load(username)
        return UsernamePasswordAuthenticationToken(authUser, null, authUserLoader.authorities(authUser))
    }

    override fun supports(authentication: Class<*>): Boolean =
        UsernamePasswordAuthenticationToken::class.java.isAssignableFrom(authentication)
}