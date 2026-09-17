package com.silverphone.app.app

import android.content.Context
import android.os.SystemClock
import coil3.ImageLoader
import com.silverphone.app.BuildConfig
import com.silverphone.app.data.local.AppDatabase
import com.silverphone.app.data.repository.ContactRepository
import com.silverphone.app.platform.contacts.SystemContactsSource
import com.silverphone.app.platform.phone.AndroidPhoneLauncher
import com.silverphone.app.platform.phone.CallPermissionGate
import com.silverphone.app.platform.phone.DialCoordinator
import com.silverphone.app.platform.phone.PhoneLauncher
import com.silverphone.app.platform.photos.PhotoBytesSource
import com.silverphone.app.platform.photos.PhotoImageLoader
import com.silverphone.app.platform.photos.PhotoNormalizer
import com.silverphone.app.platform.transfer.BackupReader
import com.silverphone.app.platform.transfer.BackupWriter
import com.silverphone.app.platform.transfer.ExportGateway
import com.silverphone.app.platform.transfer.TransferDirs
import com.silverphone.app.platform.update.GitHubReleaseSource
import com.silverphone.app.platform.update.ReleaseSource
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex

/**
 * Hand-rolled dependency container.
 *
 * The application is small enough that a full DI framework would add more
 * indirection than it removes. This object exists mainly to guarantee the
 * invariants the data and dial layers depend on: exactly one database instance,
 * exactly one write lock shared by every writer, exactly one dial lock that
 * outlives every screen, and exactly one image loader.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    /**
     * Serialises all contact-writing operations across screens. Combined with
     * Room's own transactions this gives a single, ordered write path.
     */
    val writeMutex: Mutex by lazy { Mutex() }

    /**
     * Outlives every Activity, so the dial lock and the pending request survive
     * rotation and Activity recreation without being re-issued.
     */
    /**
     * The last resort for anything running in an application-scoped coroutine.
     *
     * An uncaught exception in one of them would otherwise reach the thread's handler
     * and take the whole process down - on a phone whose owner cannot be told why. The
     * operations that matter report their own failures; this exists for the ones that
     * have not been thought of yet.
     */
    private val crashGuard = CoroutineExceptionHandler { _, _ -> }

    val applicationScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashGuard)
    }

    val contactRepository: ContactRepository by lazy {
        ContactRepository(database, writeMutex)
    }

    private val androidPhoneLauncher: AndroidPhoneLauncher by lazy {
        AndroidPhoneLauncher(appContext)
    }

    /** Starts the call. Only the foreground host may use this. */
    val phoneLauncher: PhoneLauncher get() = androidPhoneLauncher

    /** The yes/no the dial lock needs, with no Android Context in its signature. */
    val callPermission: CallPermissionGate get() = androidPhoneLauncher

    val dialCoordinator: DialCoordinator by lazy {
        DialCoordinator(
            contacts = contactRepository,
            permission = callPermission,
            scope = applicationScope,
            // Elapsed real time, not the wall clock: a clock adjustment must never
            // make the spacing check permanently reject every tap.
            clock = SystemClock::elapsedRealtime,
            countryCode = { contactRepository.countryCode() },
        )
    }

    val photoNormalizer: PhotoNormalizer by lazy { PhotoNormalizer(appContext) }

    private val photoBytesSource: PhotoBytesSource by lazy {
        PhotoBytesSource { contactId -> contactRepository.photoBytes(contactId) }
    }

    val imageLoader: ImageLoader by lazy {
        PhotoImageLoader.create(appContext, photoBytesSource)
    }

    // ------------------------------------------------------------ file transfer

    val transferDirs: TransferDirs by lazy { TransferDirs(appContext) }

    val exportGateway: ExportGateway by lazy { ExportGateway(appContext) }

    val backupWriter: BackupWriter by lazy {
        BackupWriter(
            repository = contactRepository,
            dirs = transferDirs,
            appVersion = BuildConfig.VERSION_NAME,
        )
    }

    val backupReader: BackupReader by lazy {
        BackupReader(
            contentResolver = appContext.contentResolver,
            dirs = transferDirs,
            photoNormalizer = photoNormalizer,
        )
    }

    val systemContactsSource: SystemContactsSource by lazy {
        SystemContactsSource(appContext.contentResolver)
    }

    // ---------------------------------------------------------------- updates

    /**
     * The only network call in the app, built here so nothing else can start doing
     * network work: the About screen is the single caller, and only from a tap.
     */
    val releaseSource: ReleaseSource by lazy {
        GitHubReleaseSource(appVersion = BuildConfig.VERSION_NAME)
    }
}
