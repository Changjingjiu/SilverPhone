package com.silverphone.app.domain

/**
 * Validation for the name the elderly user sees on a card.
 *
 * Length is counted in Unicode code points, not UTF-16 units, so a name built
 * from supplementary-plane characters is not silently rejected as "too long".
 * Names are never truncated: an over-long name is an error the family member
 * fixes.
 */
object DisplayNameRules {

    const val MAX_CODE_POINTS: Int = 12

    sealed interface Result {
        data class Valid(val value: String) : Result

        data class Invalid(val reason: Reason) : Result
    }

    enum class Reason {
        EMPTY,
        TOO_LONG,
        CONTROL_CHARACTER,
        INVALID_UNICODE,
    }

    fun validate(raw: String): Result {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return Result.Invalid(Reason.EMPTY)
        }
        if (trimmed.codePointCount(0, trimmed.length) > MAX_CODE_POINTS) {
            return Result.Invalid(Reason.TOO_LONG)
        }

        var index = 0
        while (index < trimmed.length) {
            val codePoint = trimmed.codePointAt(index)
            if (Character.isISOControl(codePoint)) {
                return Result.Invalid(Reason.CONTROL_CHARACTER)
            }
            // codePointAt already merges a well-formed surrogate pair, so a value
            // still inside the surrogate range here is unpaired.
            if (codePoint in 0xD800..0xDFFF) {
                return Result.Invalid(Reason.INVALID_UNICODE)
            }
            index += Character.charCount(codePoint)
        }
        return Result.Valid(trimmed)
    }
}
