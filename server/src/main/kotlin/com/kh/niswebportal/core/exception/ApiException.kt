package com.kh.niswebportal.core.exception

/**
 * Standard business domain exception for the NIS Web Portal application.
 */
class ApiException(
    val errorCode: String = "9999",
    override val message: String,
    val details: Any? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause)
