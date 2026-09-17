package com.silverphone.app.app

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The one place that knows how to put an interface language into force.
 *
 * The stored tag is the single source of truth; applying it is a platform call that
 * only AppCompat can make. Keeping both facts here means the settings screen does not
 * have to know about `LocaleListCompat`, and the application does not have to know
 * what an empty tag means.
 */
object AppLanguage {

    /** Stored value meaning "do whatever the phone is set to". */
    const val SYSTEM = ""

    /** Tags the app ships copy for. */
    val SUPPORTED = listOf("zh", "en")

    /**
     * The locale list to hand the platform for [tag].
     *
     * An empty tag becomes an empty locale list, which is AppCompat's way of saying
     * "follow the system" - the same value the settings screen writes when the family
     * picks that option.
     */
    fun localesFor(tag: String): LocaleListCompat = LocaleListCompat.forLanguageTags(tag)

    /**
     * Applies [tag] to the running app. AppCompat records the choice and rebuilds any
     * live Activity, so the change is visible at once rather than at the next launch.
     */
    fun apply(tag: String) {
        AppCompatDelegate.setApplicationLocales(localesFor(tag))
    }
}
