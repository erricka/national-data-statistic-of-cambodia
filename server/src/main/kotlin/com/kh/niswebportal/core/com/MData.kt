package com.kh.niswebportal.core.com

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.kh.niswebportal.core.builder.WhenBuilder
import com.kh.niswebportal.core.message.CommonMessageCode
import java.io.Serializable
import java.math.BigDecimal

class MData(
    map: Map<String, Any?> = mapOf()
) : LinkedHashMap<String, Any?>(map), Serializable {

    fun getString(key: String): String? = get(key)?.toString()

    fun getSafeString(key: String): String {
        return get(key)?.toString() ?: throw CommonMessageCode.MISSING_FIELDS.generateIMException(setOf(key))
    }

    fun getBigDecimal(key: String): BigDecimal {
        return get(key)?.let {
            if (getString(key).isNullOrEmpty()) BigDecimal.ZERO
            else BigDecimal(getString(key))
        } ?: BigDecimal.ZERO
    }

    fun getLong(key: String): Long = getString(key)?.toLongOrNull() ?: 0L

    fun getInt(key: String): Int = getString(key)?.toIntOrNull() ?: 0

    fun getSafeBoolean(key: String): Boolean {
        return getString(key)?.let { str ->
            when (str.lowercase()) {
                "true", "y", "1" -> true
                "false", "n", "0" -> false
                else -> false
            }
        } ?: false
    }

    fun getBoolean(key: String): Boolean? {
        return getString(key)?.toBooleanStrictOrNull()
    }

    fun getShort(key: String): Short = getString(key)?.toShortOrNull() ?: 0

    fun getDouble(key: String): Double = getString(key)?.toDoubleOrNull() ?: 0.0

    fun getFloat(key: String): Float = getString(key)?.toFloatOrNull() ?: 0.0F

    fun getMData(key: String): MData =
        try {
            when (val obj = get(key)) {
                is MData -> obj
                is Map<*, *> -> obj.map { (key, value) ->
                    key.toString() to value
                }.toMap(MData())

                else -> MData()
            }
        } catch (e: Exception) {
            MData()
        }

    fun getListMData(key: String): List<MData> {
        return try {
            when (val value = get(key)) {
                is Collection<*> -> value.mapNotNull {
                    when (it) {
                        is MData -> it
                        is Map<*, *> -> it.map { (key, value) ->
                            key.toString() to value
                        }.toMap(MData())

                        else -> MData()
                    }
                }

                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun set(key: String, value: Any?): MData {
        put(key, value)
        return this
    }

    fun set(key: String, value: () -> Any?): MData {
        return this.set(key, value())
    }

    fun setString(key: String, value: (String?) -> Any?): MData {
        return this.set(key, value(getString(key)))
    }

    fun setStringWhen(key: String, block: WhenBuilder<String?>.() -> Unit): MData {
        val builder = WhenBuilder(getString(key))
        try {
            builder.block()
        } catch (_: Exception) {
        }
        return this.setString(key, builder.result)
    }

    fun setString(key: String, value: String?): MData {
        put(key, value)
        return this
    }

    fun setString(pair: Pair<String, String?>): MData {
        return setString(pair.first, pair.second)
    }

    fun setString(key: String, value: () -> String?): MData {
        return this.setString(key, value())
    }

    fun setString(key: String, value: MData?): MData {
        return this.setString(key, value?.getString(key))
    }

    fun setStringEmptyIfNull(key: String, value: String?): MData {

        return this.setString(key, value ?: "")
    }

    fun setStringEmptyIfNull(key: String, source: MData): MData {
        return this.setStringEmptyIfNull(key, source.getString(key))
    }

    fun setStringValueIfNull(key: String, source: MData, value: String?): MData {
        return this.setStringValueIfNull(key, source.getString(key), value)
    }

    fun setStringValueIfNull(key: String, source: String?, value: String?): MData {
        return this.setString(key, source ?: value)
    }

    fun setBigDecimal(key: String, value: BigDecimal?): MData {
        put(key, value)
        return this
    }

    fun setBigDecimal(pair: Pair<String, BigDecimal?>): MData {
        return setBigDecimal(pair.first, pair.second)
    }

    fun setBigDecimal(key: String, value: () -> BigDecimal?): MData {
        return this.setBigDecimal(key, value())
    }

    fun setBigDecimal(key: String, value: MData): MData {
        return this.setBigDecimal(key, value.getBigDecimal(key))
    }

    fun setBigDecimalZeroIfNull(key: String, value: BigDecimal?): MData {
        return this.setBigDecimal(key, value ?: BigDecimal.ZERO)
    }

    fun setBigDecimalZeroIfNull(key: String, value: MData): MData {
        return this.setBigDecimalZeroIfNull(key, value.getBigDecimal(key))
    }

    fun setBigDecimalValueIfNull(key: String, source: BigDecimal?, value: BigDecimal?): MData {
        return this.setBigDecimal(key, source ?: value)
    }

    fun setBigDecimalValueIfNull(key: String, source: MData, value: BigDecimal?): MData {
        return this.setBigDecimalValueIfNull(key, source.getBigDecimal(key), value)
    }

    fun setLong(key: String, value: Long?): MData {
        put(key, value)
        return this
    }

    fun setLong(pair: Pair<String, Long?>): MData {
        return setLong(pair.first, pair.second)
    }

    fun setLong(key: String, value: () -> Long): MData {
        return this.setLong(key, value())
    }

    fun setLong(key: String, value: MData): MData {
        return this.setLong(key, value.getLong(key))
    }

    fun setLongZeroIfNull(key: String, value: Long?): MData {
        return this.setLong(key, value ?: 0L)
    }

    fun setInt(key: String, value: Int?): MData {
        put(key, value)
        return this
    }

    fun setInt(pair: Pair<String, Int?>): MData {
        return setInt(pair.first, pair.second)
    }

    fun setInt(key: String?, value: () -> Int?): MData {
        return this.setInt(key!!, value())
    }

    fun setIntZeroIfNull(key: String, value: Int?): MData {
        return this.setInt(key, value ?: 0)
    }

    fun setBoolean(key: String, value: Boolean?): MData {
        put(key, value)
        return this
    }

    fun setBoolean(pair: Pair<String, Boolean?>): MData {
        return setBoolean(pair.first, pair.second)
    }

    fun setShort(key: String, value: Short?): MData {
        put(key, value)
        return this
    }

    fun setShort(pair: Pair<String, Short?>): MData {
        return setShort(pair.first, pair.second)
    }

    fun setShort(key: String, value: () -> Int): MData {
        return this.setShort(key, value().toShort())
    }

    fun setShort(key: String, value: Int): MData {
        return this.setShort(key, value.toShort())
    }

    fun setShortZeroIfNull(key: String, value: Int?): MData {
        return this.setShort(key, value ?: 0)
    }

    fun setDouble(key: String, value: Double?): MData {
        put(key, value)
        return this
    }

    fun setDouble(pair: Pair<String, Double?>): MData {
        return setDouble(pair.first, pair.second)
    }

    fun setFloat(key: String, value: Float?): MData {
        put(key, value)
        return this
    }

    fun setFloat(pair: Pair<String, Float?>): MData {
        return setFloat(pair.first, pair.second)
    }

    fun setFloat(key: String, value: () -> Float): MData {
        return this.setFloat(key, value())
    }

    fun setFloatZeroIfNull(key: String, value: Float?): MData {
        return this.setFloat(key, value ?: 0.0F)
    }

    fun setDouble(key: String, value: () -> Double): MData {
        return this.setDouble(key, value())
    }

    fun setDoubleZeroIfNull(key: String, value: Double?): MData {
        return this.setDouble(key, value ?: 0.0)
    }

    fun setValueFrom(source: MData, vararg keys: String): MData {
        for (key in keys) {
            this.set(key, source[key])
        }
        return this
    }

    fun setValueFrom(pair: Pair<MData, Array<out String>>): MData {
        return setValueFrom(pair.first, *pair.second)
    }

    fun setMData(key: String, value: MData?): MData {
        put(key, value)
        return this
    }

    fun setMData(pair: Pair<String, MData?>): MData {
        return setMData(pair.first, pair.second)
    }

    fun setMData(key: String, value: () -> MData): MData {
        return this.setMData(key, value())
    }

    fun setMData(key: String, function: (MData) -> MData): MData {
        return this.setMData(key, function(this))
    }

    fun setListMData(key: String, value: Collection<Map<*, *>?>?): MData {
        put(key, value)
        return this
    }

    fun setListMData(pair: Pair<String, Collection<Map<*, *>?>?>): MData {
        return setListMData(pair.first, pair.second)
    }

    fun setListMData(key: String, value: () -> Collection<Map<*, *>?>?): MData {
        return this.setListMData(key, value())
    }

    fun setListMData(key: String, value: (MData) -> Collection<Map<*, *>?>?): MData {
        return this.setListMData(key, value(this))
    }

    fun appendFrom(data: MData?): MData {
        if (data != null) {
            putAll(data)
        }
        return this
    }

    fun appendMData(data: () -> MData): MData {
        return this.appendFrom(data())
    }

    fun runExpression(expression: (MData) -> Unit, inputData: MData): MData {
        expression(this.appendFrom(inputData))
        return this
    }

    fun writeString(key: String, source: MData): MData {
        try {
            this.setString(key, jacksonObjectMapper().writeValueAsString(source))
        } catch (e: Exception) {
            throw Exception("MData: writeString", e)
        }
        return this
    }

    fun writeString(pair: Pair<String, MData>): MData {
        return writeString(pair.first, pair.second)
    }


}


