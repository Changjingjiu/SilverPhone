package com.silverphone.app.domain

/**
 * Application-wide text size presets.
 *
 * The scale multiplies the base sp values and the result is then resolved by
 * Android's own font scale, so system accessibility settings are still honoured.
 *
 * The default is [STANDARD]: the base sizes were chosen to already be comfortable
 * on a phone, and the family can raise them if the elderly user needs it.
 */
enum class FontPreset(val scale: Float) {
    STANDARD(1.0f),
    LARGE(1.2f),
    EXTRA_LARGE(1.4f),
    HUGE(1.6f),
    ;

    companion object {
        val DEFAULT: FontPreset = STANDARD

        fun fromStorage(raw: String?): FontPreset? = entries.firstOrNull { it.name == raw }
    }
}
