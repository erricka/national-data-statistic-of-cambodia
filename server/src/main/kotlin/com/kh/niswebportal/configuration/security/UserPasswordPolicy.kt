package com.kh.niswebportal.configuration.security

import com.kh.niswebportal.core.com.MData
import org.springframework.core.env.Environment
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
@Component
class UserPasswordPolicy(
    private val passwordEncoder: PasswordEncoder,
    private val env: Environment
) {
    companion object {
        private const val MIN_LENGTH      = 12
        private const val UPPERCASE_COUNT = 2
        private const val LOWERCASE_COUNT = 3
        private const val NUMERIC_COUNT   = 2
        private const val SPECIAL_COUNT   = 1
        private val SPECIAL_PATTERN       = Regex("[^a-zA-Z0-9]")
    }

    fun apply(body: MData, requirePassword: Boolean) {
        val userType = body.getString("userType")?.trim()?.uppercase() ?: "SYSUSER"
        val userID   = body.getString("userID")?.trim() ?: return
        val plain    = body.getString("password")?.trim()

        if (userType == "ADMIN" || userType == "SYSUSER") {
            if (plain.isNullOrBlank()) {
                if (requirePassword) throw IllegalArgumentException("password is required")
                body.remove("password")
                return
            }
            validate(plain)
            body.setString("password", passwordEncoder.encode(buildRaw(userID, plain)))
        } else {
            body.remove("password")
        }
    }

    fun validate(password: String) {
        val violations = mutableListOf<String>()

        if (password.length < MIN_LENGTH)
            violations += "Password must be at least $MIN_LENGTH characters"
        if (password.count { it.isUpperCase() } < UPPERCASE_COUNT)
            violations += "Password must contain at least $UPPERCASE_COUNT uppercase letter(s)"
        if (password.count { it.isLowerCase() } < LOWERCASE_COUNT)
            violations += "Password must contain at least $LOWERCASE_COUNT lowercase letter(s)"
        if (password.count { it.isDigit() } < NUMERIC_COUNT)
            violations += "Password must contain at least $NUMERIC_COUNT numeric digit(s)"
        if (SPECIAL_PATTERN.findAll(password).count() < SPECIAL_COUNT)
            violations += "Password must contain at least $SPECIAL_COUNT special character(s)"

        if (violations.isNotEmpty())
            throw IllegalArgumentException(violations.joinToString("; "))
    }

    fun matches(rawInput: String, encodedHash: String): Boolean =
        passwordEncoder.matches(rawInput, encodedHash)

    private fun buildRaw(userID: String, password: String): String {
        val profile = env.activeProfiles.firstOrNull() ?: "default"
        return "$profile|$userID|$password"
    }
}