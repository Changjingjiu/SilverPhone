package com.silverphone.app.domain

import java.text.Normalizer

/**
 * The management list's search.
 *
 * Pure and separate from the ViewModel so the case that is easy to get wrong -
 * a query with no digits in it - is covered by a test. Treating "no digits" as an
 * empty needle would make `contains("")` true for every number and quietly return
 * the whole list for any name search.
 */
object ContactSearch {

    /** Characters people type inside a phone number. */
    private fun isNumberCharacter(character: Char): Boolean =
        character.isDigit() || character == '+' || character == ' ' ||
            character == '-' || character == '(' || character == ')'

    fun matches(contact: Contact, query: String): Boolean {
        val raw = query.trim()
        if (raw.isEmpty()) return true
        // Folded the same way a number is folded when it is saved. A full-width
        // keyboard produces １３８, which `isDigit` accepts but which is not the
        // character the number is stored with, so the search used to answer "no
        // results" for a contact that is right there on the phone.
        val trimmed = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        if (contact.displayName.contains(trimmed, ignoreCase = true)) return true

        // The number is only consulted when the whole query looks like a number.
        // Otherwise a mixed query such as "老伴2" would strip the digits to "2" and
        // match any number containing a 2, returning contacts the user never asked
        // for. An empty digit string must not be used either: `contains("")` is true
        // for every number.
        if (!trimmed.all(::isNumberCharacter)) return false
        // The leading '+' is dropped from the search term, not kept: a person typing
        // "+86138..." should still find a number stored as "86138...", and the other
        // way round. An empty digit string is still refused, because `contains("")`
        // is true for every number.
        val digits = trimmed.filter { it.isDigit() }
        return digits.isNotEmpty() && contact.phoneNumber.contains(digits)
    }

    fun filter(contacts: List<Contact>, query: String): List<Contact> =
        if (query.isBlank()) contacts else contacts.filter { matches(it, query) }
}
