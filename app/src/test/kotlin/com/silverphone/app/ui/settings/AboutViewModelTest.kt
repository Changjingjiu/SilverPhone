package com.silverphone.app.ui.settings

import com.silverphone.app.platform.update.ReleaseLookup
import com.silverphone.app.platform.update.ReleaseSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The update check's state machine, driven with a stand-in for GitHub.
 *
 * These tests decide what a family member is told about their copy of the app: an
 * unreachable GitHub must never read as "up to date", and a second impatient tap must
 * not start a second request.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelTest {

    private class FakeReleases(var answer: ReleaseLookup) : ReleaseSource {

        var calls: Int = 0
            private set

        private var gate: CompletableDeferred<Unit>? = null

        override suspend fun latestRelease(): ReleaseLookup {
            calls++
            gate?.await()
            return answer
        }

        /** Holds the next answer back, so the in-flight state can be observed. */
        fun hold() {
            gate = CompletableDeferred()
        }

        fun letThrough() {
            gate?.complete(Unit)
            gate = null
        }
    }

    private fun viewModel(
        releases: FakeReleases,
        installedVersionName: String = "1.0.0",
    ) = AboutViewModel(
        releases = releases,
        installedVersionName = installedVersionName,
        installedVersionCode = 1,
    )

    @Test
    fun `a newer release is offered with its page`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val releases = FakeReleases(
                ReleaseLookup.Found(
                    tag = "v1.2.0",
                    pageUrl = "https://github.com/Changjingjiu/SilverPhone/releases/tag/v1.2.0",
                ),
            )
            val model = viewModel(releases)

            assertEquals(UpdateState.NotChecked, model.update.value)
            model.checkForUpdates()
            advanceUntilIdle()

            assertEquals(
                UpdateState.Available(
                    version = "v1.2.0",
                    releaseUrl = "https://github.com/Changjingjiu/SilverPhone/releases/tag/v1.2.0",
                ),
                model.update.value,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `the published release of the installed version is up to date`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val model = viewModel(FakeReleases(ReleaseLookup.Found("v1.0.0", "https://example.invalid")))

            model.checkForUpdates()
            advanceUntilIdle()

            assertEquals(UpdateState.UpToDate, model.update.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `a debug build is offered the release with the same number`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val model = viewModel(
                releases = FakeReleases(ReleaseLookup.Found("v1.0.0", "https://example.invalid")),
                installedVersionName = "1.0.0-debug",
            )

            model.checkForUpdates()
            advanceUntilIdle()

            assertEquals(UpdateState.Available("v1.0.0", "https://example.invalid"), model.update.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `no published release is its own state, not a failure`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val model = viewModel(FakeReleases(ReleaseLookup.NotPublishedYet))

            model.checkForUpdates()
            advanceUntilIdle()

            assertEquals(UpdateState.NotPublishedYet, model.update.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `an unreachable GitHub is a failure and never up to date`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val model = viewModel(FakeReleases(ReleaseLookup.Unreachable))

            model.checkForUpdates()
            advanceUntilIdle()

            assertEquals(UpdateState.Failed, model.update.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `a tag that is not a version cannot be judged`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val model = viewModel(FakeReleases(ReleaseLookup.Found("nightly", "https://example.invalid")))

            model.checkForUpdates()
            advanceUntilIdle()

            assertEquals(UpdateState.Failed, model.update.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `a second tap while checking does not start a second request`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val releases = FakeReleases(ReleaseLookup.Found("v1.0.0", "https://example.invalid"))
            val model = viewModel(releases)
            releases.hold()

            model.checkForUpdates()
            advanceUntilIdle()
            assertEquals(UpdateState.Checking, model.update.value)

            model.checkForUpdates()
            model.checkForUpdates()
            advanceUntilIdle()
            assertEquals(1, releases.calls)

            releases.letThrough()
            advanceUntilIdle()

            assertEquals(UpdateState.UpToDate, model.update.value)
            assertEquals(1, releases.calls)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
