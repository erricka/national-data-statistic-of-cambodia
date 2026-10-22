package com.kh.niswebportal.app.settings.user

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import com.kh.niswebportal.configuration.loader.AuthUserLoader
import com.kh.niswebportal.configuration.security.RsaKeyProvider
import com.kh.niswebportal.core.annotation.NotRequirePermission
import com.kh.niswebportal.core.com.MData
import com.kh.niswebportal.core.com.MResponse
import com.kh.niswebportal.core.message.CommonMessageCode
import org.springframework.core.env.Environment
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.sql.Timestamp
import java.time.LocalDateTime

@RestController
class AuthCbc(
    private val authenticationManager: AuthenticationManager,
    private val sessionAuthStrategy: SessionAuthenticationStrategy,
    private val userEbc: UserEbc,
    private val authUserLoader: AuthUserLoader,
    private val rsaKeyProvider: RsaKeyProvider,
    private val environment: Environment
) {
    @NotRequirePermission
    @PostMapping("/auth/init")
    fun publicKey(): MResponse {
        // Generating public key automatically saves the Private Key into current HTTP Session
        val pubKey = rsaKeyProvider.publicKeyBase64
        return CommonMessageCode.SUCCESS.generateIMResponse(
            com.kh.niswebportal.core.builder.buildMData { setString("publicKey", pubKey) }
        )
    }

    @NotRequirePermission
    @PostMapping("/login")
    fun login(
        @RequestBody body: MData,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): MResponse {
        val username = body.getString("userID")?.trim()
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(messages = setOf("userID is required"))

        val encryptedPassword = body.getString("password")
            ?: return CommonMessageCode.MISSING_FIELDS.generateIMResponse(messages = setOf("password is required"))

        val decryptedPassword = try {
            rsaKeyProvider.decrypt(encryptedPassword)
        } catch (e: Exception) {
            println("RSA DECRYPTION ERROR: ${e.message}")
            e.printStackTrace()
            return CommonMessageCode.ACCESS_DENIED.generateIMResponse(
                e,
                messages = setOf("Invalid password payload / RSA decryption failed")
            )
        }

        return try {
            val param = com.kh.niswebportal.core.builder.buildMData { setString("userID", username) }

            val user = try {
                userEbc.retrieveUserByUserID(param)
            } catch (e: Exception) {
                println("USER NOT FOUND OR QUERY ERROR: ${e.message}")
                null
            }

            if (user.isNullOrEmpty()) {
                return CommonMessageCode.ACCESS_DENIED.generateIMResponse(
                    messages = setOf("Invalid user ID or password")
                )
            }

            if (user.getString("enabled") == "N" || user.getString("userStatus") == "02") {
                return CommonMessageCode.ACCOUNT_DISABLED.generateIMResponse(
                    messages = setOf("Account is disabled")
                )
            }

            val effectiveDate = user.get("effectiveDate")
            if (effectiveDate != null) {
                val effectiveDateTime = when (effectiveDate) {
                    is Timestamp -> effectiveDate.toLocalDateTime()
                    is LocalDateTime -> effectiveDate
                    is String -> LocalDateTime.parse(effectiveDate.replace(" ", "T"))
                    else -> null
                }
                if (effectiveDateTime != null && LocalDateTime.now().isBefore(effectiveDateTime)) {
                    return CommonMessageCode.ACCESS_DENIED.generateIMResponse(
                        messages = setOf("Account is not yet active. Effective date: $effectiveDateTime")
                    )
                }
            }

            val profile = environment.activeProfiles.firstOrNull() ?: "default"
            val auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(username, decryptedPassword)
            )

            sessionAuthStrategy.onAuthentication(auth, request, response)
            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = auth
            SecurityContextHolder.setContext(context)
            HttpSessionSecurityContextRepository().saveContext(context, request, response)

            user.remove("password")
            CommonMessageCode.SUCCESS.generateIMResponse(user)
        } catch (e: Exception) {
            CommonMessageCode.ACCESS_DENIED.generateIMResponse(
                e,
                messages = setOf("Invalid user ID or password")
            )
        }
    }

    @NotRequirePermission
    @PostMapping("/auth/info")
    fun me(request: HttpServletRequest, response: HttpServletResponse): MResponse {
        val repo = HttpSessionSecurityContextRepository()
        val context = repo.loadDeferredContext(request).get()
        val auth = context?.authentication
        if (auth == null || !auth.isAuthenticated || auth.name == "anonymousUser") {
            return CommonMessageCode.ACCESS_DENIED.generateIMResponse(messages = setOf("not authenticated"))
        }

        return try {
            val username = auth.name
            val param = com.kh.niswebportal.core.builder.buildMData { setString("userID", username) }
            val user = userEbc.retrieveUserByUserID(param)
            val roles = userEbc.retrieveRolesByUserID(param)
            user.remove("password")
            user.set("roles", roles)
            user.set("roleIds", roles.mapNotNull { it.getString("roleId") })

            val authUser = authUserLoader.build(username, user, roles)
            val refreshed = UsernamePasswordAuthenticationToken(authUser, null, authUserLoader.authorities(authUser))
            context.authentication = refreshed
            SecurityContextHolder.setContext(context)
            repo.saveContext(context, request, response)

            CommonMessageCode.SUCCESS.generateIMResponse(user)
        } catch (e: Exception) {
            CommonMessageCode.GENERAL_ERROR.generateIMResponse(e)
        }
    }

    @NotRequirePermission
    @PostMapping("/logout")
    fun logout(request: HttpServletRequest): MResponse {
        request.session.invalidate()
        SecurityContextHolder.clearContext()
        return CommonMessageCode.SUCCESS.generateIMResponse()
    }
}