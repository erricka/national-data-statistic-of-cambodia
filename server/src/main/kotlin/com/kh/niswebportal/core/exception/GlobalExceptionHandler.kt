package com.kh.niswebportal.core.exception

import com.kh.niswebportal.core.builder.buildMData
import com.kh.niswebportal.core.com.MException
import com.kh.niswebportal.core.com.MResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(IllegalArgumentException::class, IllegalStateException::class)
    fun handleBadRequest(ex: RuntimeException): ResponseEntity<MResponse> {
        log.warn("Bad request / business validation failure: {}", ex.message)
        val response = MResponse(
            code = "9999",
            message = MResponse.Message(
                messageCode = "9999",
                messageTitle = "Business Validation Error",
                messageBody = ex.message ?: "Invalid request parameters"
            ),
            data = buildMData {
                setString("error", ex.message)
            }
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ResponseEntity<MResponse> {
        log.warn("API Exception [{}]: {}", ex.errorCode, ex.message)
        val response = MResponse(
            code = ex.errorCode,
            message = MResponse.Message(
                messageCode = ex.errorCode,
                messageTitle = "API Error",
                messageBody = ex.message
            ),
            data = buildMData {
                setString("error", ex.message)
                set("details", ex.details)
            }
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

    @ExceptionHandler(MException::class)
    fun handleMException(ex: MException): ResponseEntity<MResponse> {
        log.warn("MException [{}]: {}", ex.code, ex.message)
        val response = MResponse(
            code = ex.code,
            message = MResponse.Message(
                messageCode = ex.code,
                messageTitle = "Operation Failed",
                messageBody = ex.message,
                messageDebug = ex.debugMessage
            ),
            data = buildMData {
                ex.metadata?.forEach { (k, v) -> set(k, v) }
            }
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<MResponse> {
        log.warn("Access denied: {}", ex.message)
        val response = MResponse(
            code = "4030",
            message = MResponse.Message(
                messageCode = "4030",
                messageTitle = "Access Denied",
                messageBody = ex.message ?: "You do not have permission to perform this action."
            )
        )
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response)
    }

    @ExceptionHandler(InsufficientAuthenticationException::class)
    fun handleUnauthorized(ex: InsufficientAuthenticationException): ResponseEntity<MResponse> {
        log.warn("Unauthorized request: {}", ex.message)
        val response = MResponse(
            code = "4010",
            message = MResponse.Message(
                messageCode = "4010",
                messageTitle = "Unauthorized",
                messageBody = ex.message ?: "Authentication required to access this resource."
            )
        )
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<MResponse> {
        log.error("Unhandled internal server error", ex)
        val response = MResponse(
            code = "5000",
            message = MResponse.Message(
                messageCode = "5000",
                messageTitle = "Internal Server Error",
                messageBody = "An unexpected error occurred. Please contact system administrator."
            )
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
    }
}
