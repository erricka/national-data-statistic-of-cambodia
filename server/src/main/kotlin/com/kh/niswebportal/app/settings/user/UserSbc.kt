package com.kh.niswebportal.app.settings.user

import com.kh.niswebportal.app.settings.user_role.UserRoleEbc
import com.kh.niswebportal.configuration.security.UserPasswordPolicy
import com.kh.niswebportal.core.com.MData
import org.springframework.core.env.Environment
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import kotlin.math.ceil
import com.kh.niswebportal.core.builder.buildMData
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException

@Service
class UserSbc(
    private val userEbc: UserEbc,
    private val userRoleEbc: UserRoleEbc,
    private val userPasswordPolicy: UserPasswordPolicy,
    private val env: Environment,
    private val authenticationManager: AuthenticationManager,
) {
    fun retrieveListUser(request: MData): MData {
        val page = request.getInt("page") ?: 0
        val size = request.getInt("size") ?: 10
        val list = userEbc.retrieveListUser(request)
        list.forEach { user ->
            val roles = userRoleEbc.retrieveRolesByUserID(com.kh.niswebportal.core.builder.buildMData {
                setString("userID", user.getString("userID"))
            })
            user.set("roles", roles)
        }
        val total = userEbc.countListUser(request)
        return com.kh.niswebportal.core.builder.buildMData {
            set("list", list)
            set("page", page)
            set("size", size)
            set("total", total)
            set("totalPages", ceil(total.toDouble() / size).toInt())
        }
    }

    fun retrieveUserByUserId(body: MData): MData {
        val user = userEbc.retrieveUserByUserID(body)
        val roles = userRoleEbc.retrieveRolesByUserID(com.kh.niswebportal.core.builder.buildMData {
            setString("userID", user.getString("userID"))
        })
        user.set("roles", roles)
        return user
    }

    fun resetPassword(body: MData): MData {
        val userID = body.getString("userID")?.trim()
            ?: throw IllegalArgumentException("userID is required")
        val currentPassword = body.getString("currentPassword")?.trim()
            ?: throw IllegalArgumentException("currentPassword is required")
        val newPassword = body.getString("newPassword")?.trim()
            ?: throw IllegalArgumentException("newPassword is required")

        val existing = userEbc.retrieveUserByUserID(com.kh.niswebportal.core.builder.buildMData {
            setString(
                "userID",
                userID
            )
        })

        val userType = existing.getString("userType")?.trim()?.uppercase()
            ?: throw IllegalArgumentException("userType not found")

        if (userType != "ADMIN" && userType != "SYSUSER") {
            throw IllegalArgumentException("Password reset not permitted for this user type")
        }

        val storedHash = existing.getString("password")
            ?: throw IllegalArgumentException("No password on record")

        val profile = env.activeProfiles.firstOrNull() ?: "default"
        val rawCurrent = "$profile|$userID|$currentPassword"
        if (!userPasswordPolicy.matches(rawCurrent, storedHash)) {
            throw IllegalArgumentException("Current password is incorrect")
        }

        userPasswordPolicy.validate(newPassword)

        val now = LocalDateTime.now()
        val date = now.toLocalDate().toString().replace("-", "")
        val time = now.toLocalTime().toString().replace(":", "").substring(0, 6)

        val resetBody = com.kh.niswebportal.core.builder.buildMData {
            setString("userID", userID)
            setString("userType", userType)
            setString("password", newPassword)
            setString("lastChangeDate", date)
            setString("lastChangeTime", time)
        }
        userPasswordPolicy.apply(resetBody, requirePassword = true)
        userEbc.updateUserPassword(resetBody)

        return com.kh.niswebportal.core.builder.buildMData { set("reset", true) }
    }

    fun registerUser(body: MData): MData {
        val userID = body.getString("userID")?.trim()
            ?: throw IllegalArgumentException("userID is required")
        userPasswordPolicy.apply(body, requirePassword = true)
        val rows = userEbc.registerUser(body)
        if (rows <= 0) throw RuntimeException("Insert failed")
        @Suppress("UNCHECKED_CAST")
        val roleIds = body.get("roleIds") as? List<String>
        if (!roleIds.isNullOrEmpty()) {
            userEbc.deleteUserRoles(com.kh.niswebportal.core.builder.buildMData { setString("userID", userID) })
            roleIds.forEach { roleId ->
                userRoleEbc.registerRoleToUser(com.kh.niswebportal.core.builder.buildMData {
                    setString("userID", userID)
                    setString("roleId", roleId)
                    setString("status", "01")
                    setString("effectiveDate", LocalDateTime.now().toLocalDate().toString().replace("-", ""))
                })
            }
        }
        return com.kh.niswebportal.core.builder.buildMData { setString("userID", userID) }
    }

    fun updateUser(body: MData): MData {
        userPasswordPolicy.apply(body, requirePassword = false)
        userEbc.updateUser(body)

        @Suppress("UNCHECKED_CAST")
        val roleIds = body.get("roleIds") as? List<String> ?: return com.kh.niswebportal.core.builder.buildMData {
            set(
                "updated",
                true
            )
        }

        val userID = body.getString("userID")
            ?: throw IllegalArgumentException("userID is required")

        val existing = userRoleEbc.retrieveRolesByUserID(com.kh.niswebportal.core.builder.buildMData {
            setString(
                "userID",
                userID
            )
        })
            .mapNotNull { it.getString("roleId") }
            .toSet()
        val incoming = roleIds.toSet()

        val toAdd = incoming - existing
        val toRemove = existing - incoming

        toAdd.forEach { roleId ->
            userRoleEbc.registerRoleToUser(com.kh.niswebportal.core.builder.buildMData {
                setString("userID", userID)
                setString("roleId", roleId)
                setString("status", "01")
                setString("effectiveDate", LocalDateTime.now().toLocalDate().toString().replace("-", ""))
            })
        }

        toRemove.forEach { roleId ->
            userRoleEbc.deleteRoleFromUser(com.kh.niswebportal.core.builder.buildMData {
                setString("userID", userID)
                setString("roleId", roleId)
            })
        }

        return com.kh.niswebportal.core.builder.buildMData { set("updated", true) }
    }

    fun deleteUser(body: MData): MData {
        val userID = body.getString("userID")?.trim()
            ?: throw IllegalArgumentException("userID is required")
        val now = LocalDateTime.now()
        val date = now.toLocalDate().toString().replace("-", "")
        val time = now.toLocalTime().toString().replace(":", "").substring(0, 6)
        val deletedBy = body.getString("deletedBy") ?: "SYSTEM"
        val data = com.kh.niswebportal.core.builder.buildMData {
            setString("userID", userID)
            setString("deletedBy", deletedBy)
            setString("deletedDate", date)
            setString("deletedTime", time)
            setString("lastChangeUser", deletedBy)
            setString("lastChangeDate", date)
            setString("lastChangeTime", time)
        }
        val rows = userEbc.deleteUser(data)
        if (rows <= 0) throw RuntimeException("Delete failed")
        userEbc.deleteUserRoles(com.kh.niswebportal.core.builder.buildMData { setString("userID", userID) })
        return com.kh.niswebportal.core.builder.buildMData { set("deleted", true) }
    }
    fun adminResetUserPasswordToDefault(body: MData): MData {
        val userID = body.getString("userID")?.trim()
            ?: throw IllegalArgumentException("userID is required")

        // 1. Retrieve existing user info to get userType
        val existing = userEbc.retrieveUserByUserID(
            com.kh.niswebportal.core.builder.buildMData { setString("userID", userID) }
        )

        val userType = existing.getString("userType")?.trim()?.uppercase()
            ?: throw IllegalArgumentException("userType not found for userID: $userID")

        // The temporary password given to a user after a reset comes from the RESET_PASSWORD
        // setting (app.security.reset-password); it is never written in the code.
        val defaultPassword = env.getProperty("app.security.reset-password")?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Password reset is not configured: set RESET_PASSWORD on the server")

        val now = LocalDateTime.now()
        val date = now.toLocalDate().toString().replace("-", "")
        val time = now.toLocalTime().toString().replace(":", "").substring(0, 6)

        // 2. Prepare reset payload including userType so userPasswordPolicy can encode buildRaw(userID, plain)
        val resetBody = com.kh.niswebportal.core.builder.buildMData {
            setString("userID", userID)
            setString("userType", userType)
            setString("password", defaultPassword)
            setString("requiresPasswordReset", "Y") // Flag user to change password on next login
            setString("lastChangeDate", date)
            setString("lastChangeTime", time)
        }

        // 3. Let userPasswordPolicy handle both validation and encoding:
        // It calls validate(defaultPassword) AND encodes password with passwordEncoder.encode(buildRaw(userID, defaultPassword))
        userPasswordPolicy.apply(resetBody, requirePassword = true)

        // 4. Update the database record with the properly encoded password
        val rows = userEbc.updateUserPassword(resetBody)
        if (rows <= 0) throw RuntimeException("Failed to reset password for userID: $userID")

        return com.kh.niswebportal.core.builder.buildMData {
            set("reset", true)
            setString("userID", userID)
        }
    }

    /**
     * Logged-in user changes their own password.
     * [userID] must come from the session (never from the request body).
     */
    fun changeOwnPassword(userID: String, currentPassword: String, newPassword: String): MData {
        if (currentPassword.isBlank() || newPassword.isBlank()) {
            throw IllegalArgumentException("Current password and new password are required")
        }
        if (currentPassword == newPassword) {
            throw IllegalArgumentException("New password must be different from the current password")
        }

        // 1. Verify the current password exactly the way /login does
        try {
            authenticationManager.authenticate(UsernamePasswordAuthenticationToken(userID, currentPassword))
        } catch (e: AuthenticationException) {
            throw IllegalArgumentException("Current password is incorrect")
        }

        val existing = userEbc.retrieveUserByUserID(buildMData { setString("userID", userID) })
        val userType = existing.getString("userType")?.trim()?.uppercase()
            ?: throw IllegalArgumentException("userType not found")

        val now = LocalDateTime.now()
        val body = buildMData {
            setString("userID", userID)
            setString("userType", userType)
            setString("password", newPassword)
            setString("lastChangeUser", userID)
            setString("lastChangeDate", now.toLocalDate().toString().replace("-", ""))
            setString("lastChangeTime", now.toLocalTime().toString().replace(":", "").substring(0, 6))
        }

        // 2. Same policy as register/reset: validates strength and encodes "password"
        userPasswordPolicy.apply(body, requirePassword = true)

        // 3. Save the new hash and clear the "must change password" flag
        val rows = userEbc.changeUserPassword(body)
        if (rows <= 0) throw RuntimeException("Password change failed")

        return buildMData { set("changed", true) }
    }
}