package com.kh.niswebportal.core.annotation

import org.springframework.security.access.prepost.PreAuthorize

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("@permissionChecker.hasPermission()")
annotation class RequirePermission