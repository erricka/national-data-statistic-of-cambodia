package com.kh.niswebportal.core.util

import org.apache.commons.csv.CSVRecord
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

/**
 * Centralized utility for Khmer language numeral conversions, calendar month mappings,
 * and JDBC/CSV data parsing helpers used across migrator and inquiry services.
 */
object KhmerStringUtils {

    private val KHMER_TO_LATIN_DIGITS = mapOf(
        '\u17e0' to '0', '\u17e1' to '1', '\u17e2' to '2', '\u17e3' to '3', '\u17e4' to '4',
        '\u17e5' to '5', '\u17e6' to '6', '\u17e7' to '7', '\u17e8' to '8', '\u17e9' to '9'
    )

    private val KHMER_MONTH_NAMES = arrayOf(
        "",         // 0-index unused
        "មករា",     // 1: January
        "កុម្ភៈ",     // 2: February
        "មីនា",     // 3: March
        "មេសា",     // 4: April
        "ឧសភា",     // 5: May
        "មិថុនា",    // 6: June
        "កក្កដា",    // 7: July
        "សីហា",     // 8: August
        "កញ្ញា",     // 9: September
        "តុលា",     // 10: October
        "វិច្ឆិកា",   // 11: November
        "ធ្នូ"       // 12: December
    )

    /**
     * Converts any Khmer numerals in the input string to standard Latin digits.
     */
    fun convertKhmerNumerals(input: String): String {
        return input.map { KHMER_TO_LATIN_DIGITS[it] ?: it }.joinToString("")
    }

    /**
     * Parses numeric text containing Khmer/Latin digits into a Double.
     * Returns null if blank, empty, or "-" (represented as missing/null value).
     */
    fun parseKhmerDouble(text: String?): Double? {
        if (text.isNullOrBlank() || text.trim() == "-") return null
        val normalized = convertKhmerNumerals(text)
        val cleanString = normalized.replace(Regex("[^0-9.-]"), "")
        return cleanString.toDoubleOrNull()
    }

    /**
     * Parses numeric text containing Khmer/Latin digits into an Int.
     */
    fun parseKhmerInt(text: String?): Int? {
        return parseKhmerDouble(text)?.toInt()
    }

    /**
     * Maps Khmer month text (e.g. "មករា", "កុម្ភៈ") to an integer (1..12).
     * Returns null if no recognized month name is found.
     */
    fun mapKhmerMonth(monthText: String): Int? {
        return when {
            monthText.contains("មករា") -> 1
            monthText.contains("កុម្ភៈ") -> 2
            monthText.contains("មីនា") -> 3
            monthText.contains("មេសា") -> 4
            monthText.contains("ឧសភា") -> 5
            monthText.contains("មិថុនា") -> 6
            monthText.contains("កក្កដា") -> 7
            monthText.contains("សីហា") -> 8
            monthText.contains("កញ្ញា") -> 9
            monthText.contains("តុលា") -> 10
            monthText.contains("វិច្ឆិកា") -> 11
            monthText.contains("ធ្នូ") -> 12
            else -> null
        }
    }

    /**
     * Gets the Khmer month name by month number (1..12).
     */
    fun getKhmerMonthName(month: Int): String {
        return if (month in 1..12) KHMER_MONTH_NAMES[month] else ""
    }
}

// ----------------------------------------------------------------------------
// Extension Functions for cleaner, idiomatic Kotlin across services
// ----------------------------------------------------------------------------

/**
 * Safely extracts a nullable Double from a JDBC ResultSet, checking for SQL NULL.
 */
fun ResultSet.getNullableDouble(columnName: String): Double? {
    val value = this.getDouble(columnName)
    return if (this.wasNull()) null else value
}

/**
 * Sets a nullable Double on a JDBC PreparedStatement.
 */
fun PreparedStatement.setNullableDouble(index: Int, value: Double?) {
    if (value != null) {
        this.setDouble(index, value)
    } else {
        this.setNull(index, Types.DOUBLE)
    }
}

/**
 * Safely gets a column value from an Apache Commons CSVRecord by index without throwing IndexOutOfBoundsException.
 */
fun CSVRecord.getOrNull(index: Int): String? {
    return if (index in 0 until this.size()) this.get(index) else null
}
