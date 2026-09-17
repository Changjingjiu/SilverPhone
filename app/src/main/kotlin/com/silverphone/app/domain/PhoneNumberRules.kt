package com.silverphone.app.domain

import java.text.Normalizer

/**
 * Normalises the single phone number attached to a card.
 *
 * Passing this check only means the number is syntactically dialable; it says
 * nothing about whether the carrier can reach it. The rules deliberately do not
 * assume Chinese mobile numbers: landlines, short service numbers and numbers
 * with an explicit country code all pass, and nothing is added or trimmed.
 */
object PhoneNumberRules {

    const val MIN_DIGITS: Int = 3
    const val MAX_DIGITS: Int = 20

    private val REMOVED_SEPARATORS = charArrayOf(' ', '\u00A0', '-', '(', ')')

    sealed interface Result {
        data class Valid(val normalized: String) : Result

        data class Invalid(val reason: Reason) : Result
    }

    enum class Reason {
        EMPTY,
        TOO_SHORT,
        TOO_LONG,
        ILLEGAL_CHARACTERS,
        MISPLACED_PLUS,
    }

    fun normalize(raw: String): Result {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return Result.Invalid(Reason.EMPTY)
        }

        // NFKC folds full-width digits, plus signs and parentheses onto their
        // ASCII forms so those inputs are recognised rather than rejected.
        val folded = Normalizer.normalize(trimmed, Normalizer.Form.NFKC)

        val stripped = StringBuilder(folded.length)
        for (character in folded) {
            if (character in REMOVED_SEPARATORS) continue
            stripped.append(character)
        }
        if (stripped.isEmpty()) {
            return Result.Invalid(Reason.EMPTY)
        }

        var index = 0
        if (stripped[0] == '+') {
            index = 1
        }

        var digits = 0
        while (index < stripped.length) {
            val character = stripped[index]
            when {
                character == '+' -> return Result.Invalid(Reason.MISPLACED_PLUS)
                character in '0'..'9' -> digits++
                // Anything else - letters, '/', '*', '#', ',', ';', a URI scheme -
                // is rejected rather than stripped.
                else -> return Result.Invalid(Reason.ILLEGAL_CHARACTERS)
            }
            index++
        }

        if (digits < MIN_DIGITS) return Result.Invalid(Reason.TOO_SHORT)
        if (digits > MAX_DIGITS) return Result.Invalid(Reason.TOO_LONG)

        return Result.Valid(stripped.toString())
    }
}
