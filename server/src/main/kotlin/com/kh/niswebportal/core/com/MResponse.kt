package com.kh.niswebportal.core.com

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.io.Serializable

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class MResponse(
    val code: String,
    val message: Message? = Message(),
    val data: MData = MData()
) : Serializable {
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class Message(
        @JsonIgnore
        val messageCode: String? = null,
        val messageTitle: String? = "Success",
        val messageBody: String? = null,
        val messageDebug: String? = null,
        @JsonIgnore
        val exception: Exception? = null
    ) : Serializable
}
