package com.kh.niswebportal.core.message

import com.kh.niswebportal.core.com.MData
import com.kh.niswebportal.core.com.MException
import com.kh.niswebportal.core.com.MResponse
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

interface ResponseMessageCode {
    val value: String
    val description: String
    val km: String get() = description
    val ko: String get() = description

    fun currentLanguage(): String {
        val attrs = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes ?: return "en"
        return attrs.request.getHeader("language")?.lowercase() ?: "en"
    }

    fun localizedDescription(): String = when (currentLanguage()) {
        "km" -> km
        "ko" -> ko
        else -> description
    }

    fun generateIMResponse(body: MData = MData(), messages: Set<String?> = emptySet()): MResponse {
        val title = localizedDescription()
        val debugMessage = (listOfNotNull(description) + messages).joinToString(": \n")
        return MResponse(
            code = value,
            message = MResponse.Message(
                messageBody = title,
                messageTitle = title,
                messageDebug = debugMessage
            ),
            data = body
        )
    }

    fun generateIMResponse(e: Exception, messages: Set<String?> = emptySet()): MResponse =
        MResponse(
            code = value,
            message = generateIMMessage(e, messages)
        )

    fun generateIMMessage(e: Exception, messages: Set<String?> = emptySet()): MResponse.Message {
        return MResponse.Message(
            messageCode = value,
            messageTitle = localizedDescription(),
            messageBody = e.message,
            messageDebug = e.message,
            exception = e
        )
    }

    fun generateIMException(e: Exception, messages: Set<String?> = emptySet()): MException {
        val relevantTraces = e.stackTrace
            .take(3)
            .map { it.toString() }
            .toSet()
        return generateIMException(setOf(e.message, "\n") + relevantTraces + messages)
    }

    fun generateIMException(messages: Set<String?> = emptySet()): MException {
        val debugMessage = (listOfNotNull(description) + messages).joinToString(": \n")
        return MException(value, debugMessage, localizedDescription())
    }
}