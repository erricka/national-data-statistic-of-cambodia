package com.kh.niswebportal.core.com

import com.kh.niswebportal.core.annotation.NotRequirePermission
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.stereotype.Component

@Aspect
@Component
class GlobalPermissionAspect(private val permissionChecker: PermissionChecker) {

    @Before("within(@org.springframework.web.bind.annotation.RestController *)")
    fun checkPermission(joinPoint: JoinPoint) {
        val method = (joinPoint.signature as MethodSignature).method
        if (method.isAnnotationPresent(NotRequirePermission::class.java)) return
        permissionChecker.hasPermission()
    }
}