package com.silverphone.app.domain

/**
 * The six placeholder backgrounds a contact without a photo may use.
 *
 * Stored by name so the value survives schema changes, and never chosen at
 * random: the colour is part of how the elderly user recognises a card, so it has
 * to stay the same on every launch.
 */
enum class PlaceholderColor {
    LIGHT_BLUE,
    LIGHT_AMBER,
    LIGHT_PURPLE,
    LIGHT_TEAL,
    LIGHT_ROSE,
    LIGHT_GRAY,
    ;

    companion object {
        val DEFAULT: PlaceholderColor = LIGHT_BLUE

        /** Returns null for an unknown value so callers decide how to react. */
        fun fromStorage(raw: String?): PlaceholderColor? =
            entries.firstOrNull { it.name == raw }
    }
}
