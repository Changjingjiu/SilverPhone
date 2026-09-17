package com.silverphone.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.silverphone.app.app.AppContainer
import com.silverphone.app.app.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Housekeeping starts only here, before any screen can start an operation,
        // which is what makes it safe to drop working directories unconditionally:
        // nothing is in flight at this moment. Exports are still protected by their
        // own age limit, so a receiving app has time to read a file that was just
        // shared. It runs off the main thread because deleting directories is disk
        // work, and none of it has to be finished before the first frame.
        container.applicationScope.launch(Dispatchers.IO) {
            container.transferDirs.cleanUp()
        }

        applyStoredLanguage()
    }

    /**
     * Puts the family's language choice in force before the first Activity attaches
     * its base context.
     *
     * Without this the app came up in the system language on every launch and only
     * switched in the moment someone pressed Save, so a family who chose Chinese had
     * to choose it again after every restart.
     *
     * The read blocks on purpose. It is one row on a local database, and it has to be
     * resolved before the first screen exists: applying the locale later means the
     * Activity is recreated and the interface visibly flips language after it has
     * already been drawn. If the database cannot be read at all the app follows the
     * system, which is the same as having no choice stored.
     */
    private fun applyStoredLanguage() {
        // A language chosen in Android's own settings (API 33+) belongs to the platform
        // and outranks the copy in the database: silently reverting the choice someone
        // made in the system UI is the kind of thing that makes an app feel broken.
        if (!AppCompatDelegate.getApplicationLocales().isEmpty) return
        val tag = runCatching {
            runBlocking { container.contactRepository.languageTag() }
        }.getOrDefault(AppLanguage.SYSTEM)
        AppLanguage.apply(tag)
    }
}
