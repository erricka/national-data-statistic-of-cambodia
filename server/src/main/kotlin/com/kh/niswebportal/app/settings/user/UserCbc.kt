package com.kh.niswebportal.app.settings.user

import com.kh.niswebportal.core.annotation.NotRequirePermission
import com.kh.niswebportal.core.annotation.RequirePermission
import com.kh.niswebportal.core.com.MData
import com.kh.niswebportal.core.com.MResponse
import com.kh.niswebportal.core.message.CommonMessageCode
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import com.kh.niswebportal.configuration.security.RsaKeyProvider
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.web.context.HttpSessionSecurityContextRepository

@RestController
class UserCbc(
    private val userSbc: UserSbc,
    private val rsaKeyProvider: RsaKeyProvider,
) {
    @RequirePermission
    @PostMapping("/USRI001")
    fun retrieveListUser(@RequestBody request: MData): MResponse {
        return try {
            CommonMessageCode.SUCCESS.generateIMResponse(userSbc.retrieveListUser(request))
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @NotRequirePermission
    @PostMapping("/USRI002")
    fun retrieveUserByUserId(@RequestBody body: MData): MResponse {
        return try {
            CommonMessageCode.SUCCESS.generateIMResponse(userSbc.retrieveUserByUserId(body))
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @RequirePermission
    @PostMapping("/USRR001")
    fun registerUser(@RequestBody body: MData): MResponse {
        body.getString("userID")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(messages = setOf("userID is required"))
        return try {
            CommonMessageCode.SUCCESS.generateIMResponse(userSbc.registerUser(body))
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @NotRequirePermission
    @PostMapping("/USRU001")
    fun updateUser(@RequestBody body: MData): MResponse {
        return try {
            CommonMessageCode.SUCCESS.generateIMResponse(userSbc.updateUser(body))
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @RequirePermission
    @PostMapping("/USRU002")
    fun resetPassword(@RequestBody body: MData): MResponse {
        return try {
            CommonMessageCode.SUCCESS.generateIMResponse(userSbc.resetPassword(body))
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @PostMapping("/USRU003")
    fun resetPasswordByAdminToDefault(@RequestBody body: MData): MResponse {
        return try {
            CommonMessageCode.SUCCESS.generateIMResponse(userSbc.adminResetUserPasswordToDefault(body))
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @PostMapping("/USRD001")
    fun deleteUser(@RequestBody body: MData): MResponse {
        return try {
            CommonMessageCode.SUCCESS.generateIMResponse(userSbc.deleteUser(body))
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    // Change own password (any logged-in user). Passwords are RSA-encrypted like /login.
    @NotRequirePermission
    @PostMapping("/USRU004")
    fun changeOwnPassword(@RequestBody body: MData, request: HttpServletRequest): MResponse {
        // User comes from the session, same as /auth/info — never from the body
        val auth = HttpSessionSecurityContextRepository().loadDeferredContext(request).get()?.authentication
        if (auth == null || !auth.isAuthenticated || auth.name == "anonymousUser") {
            return CommonMessageCode.ACCESS_DENIED.generateIMResponse(messages = setOf("not authenticated"))
        }

        val encryptedCurrent = body.getString("currentPassword")
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(messages = setOf("currentPassword is required"))
        val encryptedNew = body.getString("newPassword")
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(messages = setOf("newPassword is required"))

        val (currentPassword, newPassword) = try {
            rsaKeyProvider.decrypt(encryptedCurrent) to rsaKeyProvider.decrypt(encryptedNew)
        } catch (e: Exception) {
            return CommonMessageCode.ACCESS_DENIED.generateIMResponse(
                e, messages = setOf("Invalid password payload / RSA decryption failed")
            )
        }

        return try {
            CommonMessageCode.SUCCESS.generateIMResponse(
                userSbc.changeOwnPassword(auth.name, currentPassword, newPassword)
            )
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }
}