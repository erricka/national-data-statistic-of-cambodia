package com.kh.niswebportal.app.datasource

object FieldValidators {
    fun validate(rule: FieldRule, raw: String): String? {
        val v = raw.trim()
        if (v.isEmpty()) {
            return if (rule.required) "'${rule.name}' is required" else null
        }
        return when (rule.type) {
            FieldType.AMOUNT  -> if (v.toBigDecimalOrNull() == null) "'${rule.name}' must be a decimal (got '$v')" else null
            FieldType.NUMERIC -> if (v.toLongOrNull()       == null) "'${rule.name}' must be a number (got '$v')"  else null
            FieldType.TEXT    -> null
        }
    }
}