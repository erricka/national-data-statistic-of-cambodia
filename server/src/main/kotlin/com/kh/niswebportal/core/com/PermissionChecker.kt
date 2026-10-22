package com.kh.niswebportal.core.com

import com.kh.niswebportal.app.settings.user.UserEbc
import com.kh.niswebportal.app.settings.user_role.UserRoleEbc
import com.kh.niswebportal.configuration.loader.AuthContext
import com.kh.niswebportal.core.annotation.NotRequirePermission
import org.slf4j.LoggerFactory
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.util.concurrent.ConcurrentHashMap

@Component("permissionChecker")
class PermissionChecker(
    private val userRoleEbc: UserRoleEbc,
    private val userEbc: UserEbc,
    private val requestMappingHandlerMapping: RequestMappingHandlerMapping
) {
    private val log = LoggerFactory.getLogger(PermissionChecker::class.java)

    private data class CachedPermissions(
        val permissions: Set<String>,
        val expiresAt: Long
    )

    private val permissionCache = ConcurrentHashMap<String, CachedPermissions>()
    private val cacheTtlMillis = 5 * 60 * 1000L // 5 minutes TTL

    fun hasPermission(): Boolean {
        val auth = SecurityContextHolder.getContext().authentication
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
            ?: throw InsufficientAuthenticationException("Unauthorized request context")

        if (auth == null || !auth.isAuthenticated || auth.name == "anonymousUser") {
            throw InsufficientAuthenticationException("User is not authenticated")
        }

        // 1. Bypass check if route is annotated with @NotRequirePermission
        val handler = try {
            requestMappingHandlerMapping.getHandler(request)?.handler as? HandlerMethod
        } catch (_: Exception) {
            null
        }
        if (handler?.hasMethodAnnotation(NotRequirePermission::class.java) == true) {
            return true
        }

        val authUser = AuthContext.current()
        val userId = authUser?.userId ?: auth.name

        // 2. Bypass check if user is ADMIN (from AuthContext or DB fallback)
        if (authUser?.userType == "ADMIN") {
            return true
        }

        // 3. Extract target permission code from URI (e.g., /api/nis-portal/DTSI009 -> DTSI009)
        val permissionCode = request.requestURI.trimStart('/').substringAfterLast('/')

        // 4. Fetch permissions using TTL in-memory cache to avoid N+1 DB round-trips
        val userPermissions = getCachedOrFetchPermissions(userId)

        // 5. Verify if permission is assigned to user
        if (!userPermissions.contains(permissionCode)) {
            log.warn("SECURITY BLOCK: User '{}' lacks permission '{}'", userId, permissionCode)
            throw AccessDeniedException("User '$userId' does not have permission '$permissionCode'")
        }

        return true
    }

    private fun getCachedOrFetchPermissions(userId: String): Set<String> {
        val now = System.currentTimeMillis()
        val cached = permissionCache[userId]
        if (cached != null && cached.expiresAt > now) {
            return cached.permissions
        }

        // Check if user is ADMIN in database (fallback if not in AuthContext)
        val userData = com.kh.niswebportal.core.builder.buildMData { setString("userID", userId) }
        val userProfile = userEbc.retrieveUserByUserID(userData)
        if (userProfile?.getString("userType") == "ADMIN") {
            val allPerms = setOf("*")
            permissionCache[userId] = CachedPermissions(allPerms, now + cacheTtlMillis)
            return allPerms
        }

        val permissions = userRoleEbc.retrievePermissionsByUserID(userData)
            .mapNotNull { it.getString("permissionId") }
            .toSet()

        permissionCache[userId] = CachedPermissions(permissions, now + cacheTtlMillis)
        return permissions
    }

    fun evictUserPermissionCache(userId: String? = null) {
        if (userId != null) {
            permissionCache.remove(userId)
        } else {
            permissionCache.clear()
        }
    }
}
