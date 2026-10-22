package com.kh.niswebportal.app.datasource

import kotlin.reflect.full.memberProperties

enum class FieldType { AMOUNT, NUMERIC, TEXT }

data class FieldRule(
    val index:    Int,
    val name:     String,
    val type:     FieldType,
    val required: Boolean = true,
)

open class SourceSchema(
    val sourceId: String,
    rules:        Array<FieldRule>,
) {
    open val totalColumns: Int = (rules.maxOfOrNull { it.index } ?: -1) + 1
    open val rulesByIndex: Array<FieldRule?> = arrayOfNulls<FieldRule>(totalColumns).also { slots ->
        for (r in rules) {
            require(r.index in 0 until totalColumns) { "Rule index ${r.index} out of bounds for '$sourceId'" }
            require(slots[r.index] == null) { "Duplicate rule at index ${r.index} for '$sourceId'" }
            slots[r.index] = r
        }
    }
}

class UnvalidatedSchema(sourceId: String, count: Int) : SourceSchema(sourceId, emptyArray()) {
    override val totalColumns: Int = count
    override val rulesByIndex: Array<FieldRule?> = arrayOfNulls(count)
}
object DatasourceRegistry {
    private val schemas: Map<String, SourceSchema> by lazy {
        Schemas::class.memberProperties
            .mapNotNull { it.getter.call(Schemas) as? SourceSchema }
            .associateBy { it.sourceId.uppercase() }
    }
    fun get(sourceId: String): SourceSchema? = schemas[sourceId.uppercase()]
}