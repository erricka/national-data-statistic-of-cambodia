package com.kh.niswebportal.core.com

class MException(
    val code: String,
    val debugMessage: String? = null,
    message: String? = null,
    cause: Throwable? = null,
    val metadata: Map<String, Any>? = null
) : RuntimeException(message, cause)