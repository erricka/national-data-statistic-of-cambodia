package com.kh.niswebportal.core.builder

class WhenBuilder<T>(private val subject: T) {
    lateinit var result: String
    private var matched = false

    infix fun String.then(value: String) {
        if (!matched && this == subject) {
            result = value
            matched = true
            throw Exception()
        }
    }

    infix fun T?.then(value: String) {
        if (!matched && this == subject) {
            result = value
            matched = true
            throw Exception()
        }
    }

    fun otherwise(value: String) {
        if (!matched) {
            result = value
            throw Exception()
        }
    }

    operator fun String.invoke(value: String) {
        this then value
    }

    operator fun T?.invoke(value: String) {
        this then value
    }
}