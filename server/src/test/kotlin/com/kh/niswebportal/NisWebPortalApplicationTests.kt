package com.kh.niswebportal

import com.kh.niswebportal.app.settings.user.UserSbc
import com.kh.niswebportal.configuration.security.UserPasswordPolicy
import com.kh.niswebportal.core.builder.buildMData
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("default")
class NisWebPortalApplicationTests {

    // Password for the seed accounts below; set SEED_PASSWORD before running these tests.
    private val seedPassword: String = System.getenv("SEED_PASSWORD") ?: "change-me"

    @Autowired
    private lateinit var userSbc: UserSbc

    @Autowired
    private lateinit var userPasswordPolicy: UserPasswordPolicy

    @Autowired private lateinit var authenticationManager: org.springframework.security.authentication.AuthenticationManager
    @Autowired private lateinit var userEbc: com.kh.niswebportal.app.settings.user.UserEbc
    @Autowired private lateinit var environment: org.springframework.core.env.Environment

    /**
     * Generates the exact BCrypt hash using userPasswordPolicy.apply
     */
    fun generateHashedPassword(userID: String, rawPassword: String, userType: String = "ADMIN"): String {
        val tempMData = buildMData {
            setString("userID", userID)
            setString("userType", userType)
            setString("password", rawPassword)
        }

        userPasswordPolicy.apply(tempMData, requirePassword = true)

        return tempMData.getString("password")
            ?: throw IllegalStateException("Failed to extract encoded password from MData")
    }

    @Test
    fun printHashedPasswordForDatabaseSeed() {
        val adminUserID = "developer"
        val rawPassword = seedPassword

        val adminHash = generateHashedPassword(adminUserID, rawPassword, "ADMIN")

        println("==================================================")
        println("ADMIN USER HASH ($adminUserID)    : $adminHash")
        println("==================================================")
    }

    @Test
    fun seedAdminUser() {
        val userPayload = buildMData {
            setString("userID", "admin")
            setString("userName", "Administrator")
            setString("firstName", "System")
            setString("lastName", "Admin")
            setString("email", "admin@nis.gov.kh")
            setString("password", seedPassword) // Or generateHashedPassword(seedPassword)
            setString("userType", "ADMIN")
            setString("userStatus", "01")
            setString("enabled", "Y")
            setString("requiresPasswordReset", "N")
        }

        userSbc.registerUser(userPayload)
    }

    @Test
    fun seedReviewerUser() {
        val userPayload = buildMData {
            setString("userID", "reviewer")
            setString("userName", "Reviewer User")
            setString("firstName", "Data")
            setString("lastName", "Reviewer")
            setString("email", "reviewer@nis.gov.kh")
            setString("password", seedPassword)
            setString("userType", "SYSUSER")
            setString("userStatus", "01")
            setString("enabled", "Y")
            setString("requiresPasswordReset", "N")
        }

        userSbc.registerUser(userPayload)
    }

    @Test
    fun seedUoloadUser() {
        val userPayload = buildMData {
            setString("userID", "05_user")
            setString("userName", "05 User")
            setString("firstName", "05")
            setString("lastName", "User")
            setString("email", "05user@nis.gov.kh")
            setString("password", seedPassword)
            setString("userType", "SYSUSER")
            setString("userStatus", "01")
            setString("enabled", "Y")
            setString("requiresPasswordReset", "N")
        }

        userSbc.registerUser(userPayload)
    }

    @Test
    fun reproduceChangePassword() {
        val userID = "reviewer"
        val original = seedPassword
        val newPw = "TestNewPass99!!"

        fun loginResult(pw: String): String {
            val e = runCatching {
                authenticationManager.authenticate(
                    org.springframework.security.authentication.UsernamePasswordAuthenticationToken(userID, pw)
                )
            }.exceptionOrNull()
            return if (e == null) "OK" else "FAILED (${e.javaClass.simpleName}: ${e.message})"
        }

        // 1. Restore a known password the same way the seed does
        val restore = buildMData {
            setString("userID", userID)
            setString("userType", "SYSUSER")
            setString("password", original)
            setString("lastChangeDate", "20260924")
            setString("lastChangeTime", "000000")
        }
        userPasswordPolicy.apply(restore, requirePassword = true)
        userEbc.updateUserPassword(restore)

        println("==================================================")
        println("1. Login with ORIGINAL after restore      : ${loginResult(original)}")

        // 2. Change it through the real service used by USRU004
        val change = runCatching { userSbc.changeOwnPassword(userID, original, newPw) }
        println("2. changeOwnPassword                      : ${change.exceptionOrNull()?.message ?: "OK"}")
        println("3. Login with NEW after change            : ${loginResult(newPw)}")
        println("4. Login with ORIGINAL after change       : ${loginResult(original)}")

        // 3. Discover which text apply() actually hashes
        val stored = userEbc.retrieveUserByUserID(buildMData { setString("userID", userID) }).getString("password")!!
        val candidates = mapOf(
            "password only" to newPw,
            "userID|password" to "$userID|$newPw",
            "default|userID|password" to "default|$userID|$newPw",
            "default|SYSUSER|userID|password" to "default|SYSUSER|$userID|$newPw",
            "SYSUSER|userID|password" to "SYSUSER|$userID|$newPw",
        )
        candidates.forEach { (name, raw) ->
            println("   stored hash = $name ? ${userPasswordPolicy.matches(raw, stored)}")
        }
        println("==================================================")
    }
}