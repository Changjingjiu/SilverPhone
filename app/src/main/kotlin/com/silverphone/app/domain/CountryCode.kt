package com.silverphone.app.domain

/**
 * The dialling prefix used for numbers the family stored without a country code.
 *
 * The app is not tied to one country: a family in China leaves this at +86, a family
 * in the United States sets +1, and the same build serves both. Applying it at dial
 * time rather than on save means changing it never rewrites stored contacts, and an
 * imported archive keeps working on either side of the world.
 *
 * The prefix is never added to a number that already carries one, so an explicitly
 * international number is left exactly as the family typed it.
 */
object CountryCode {

    const val DEFAULT: String = "+86"

    /** Offered in settings; the family can also type their own. */
    val PRESETS: List<String> = listOf(
        "+86", // China
        "+1", // North America
        "+44", // United Kingdom
        "+81", // Japan
        "+82", // South Korea
        "+852", // Hong Kong
        "+853", // Macao
        "+886", // Taiwan
        "+65", // Singapore
        "+60", // Malaysia
        "+61", // Australia
        "+64", // New Zealand
        "+49", // Germany
        "+33", // France
        "+39", // Italy
        "+34", // Spain
        "+7", // Russia / Kazakhstan
        "+91", // India
        "+62", // Indonesia
        "+66", // Thailand
        "+84", // Vietnam
        "+63", // Philippines
        "+55", // Brazil
        "+52", // Mexico
        "+27", // South Africa
    )

    /**
     * Shortest number that gets the prefix.
     *
     * Service and emergency numbers are deliberately left alone: prepending +86 to
     * 10086 or 110 would dial something else entirely, and those short codes are
     * exactly what people store for banks and hotlines. Six digits is comfortably
     * below any real subscriber number and above every short code in common use.
     */
    private const val MIN_DIGITS_FOR_PREFIX = 6

    /**
     * The one country in [PRESETS] whose national numbers keep their leading zero
     * abroad. Everywhere else the zero is a domestic trunk prefix - the thing a
     * country code is there to replace.
     */
    private const val ITALY = "+39"

    /** A stored setting is valid when it is empty or "+" followed by 1 to 4 digits. */
    fun validate(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return true
        if (!trimmed.startsWith("+")) return false
        val digits = trimmed.substring(1)
        return digits.length in 1..4 && digits.all { it in '0'..'9' }
    }

    fun normalize(raw: String): String = raw.trim()

    /**
     * The number that will actually be dialled.
     *
     * [countryCode] empty means "add nothing", which is how a family who stores full
     * international numbers or wants the phone's own behaviour can opt out.
     *
     * A leading zero is dropped before the prefix is added. That zero is the domestic
     * trunk prefix, and it is precisely what the country code replaces: a Beijing
     * landline is stored as 01012345678 and is dialled as +86 10 1234 5678, not as
     * +86 010 1234 5678, which the network rejects. Without this, an imported landline
     * could never be reached and the failure was silent.
     */
    fun apply(number: String, countryCode: String): String {
        val prefix = normalize(countryCode)
        if (prefix.isEmpty()) return number
        // Already has a country code, or came from an archive as an international
        // number: leave it alone.
        if (number.startsWith("+")) return number
        val digitCount = number.count { it in '0'..'9' }
        if (digitCount < MIN_DIGITS_FOR_PREFIX) return number
        if (prefix == ITALY) return prefix + number
        return prefix + number.removePrefix("0")
    }

    /** Human-readable form of what dialling this contact will do, for the editor. */
    fun describe(number: String, countryCode: String): String = apply(number, countryCode)
}
