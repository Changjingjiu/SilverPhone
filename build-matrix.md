# Build matrix — verified dependency set

Recorded: 2026-09-17, on macOS 24.6.0 (arm64), JDK 17.

This file records what was **actually built and run**, not what the design documents
hoped for. Where a version had to differ from the documents, the reason is stated.

## 1. Toolchain

| Component | Version | Notes |
|---|---|---|
| JDK | Temurin 17.0.20.1+1 | `java-17` from `java_home`. The machine originally had only a JRE, so a JDK was installed; AGP 8.13 requires JDK 17+. |
| Gradle | 8.13 | Wrapper committed (`gradlew`, `gradle/wrapper/`). |
| Android Gradle Plugin | 8.13.2 | |
| Kotlin | 2.2.21 | Same version used for `org.jetbrains.kotlin.plugin.compose` and `plugin.serialization`. |
| KSP | 2.2.21-2.0.5 | Classic pairing scheme (Kotlin version + KSP version). |
| Android SDK platform | android-36 | Also installed: 33, 34, 35. |
| Build tools | 36.1.0 | |
| compileSdk | 36 | |
| targetSdk | 36 | |
| **minSdk** | **23** | Product contract. Verified in the built APK, see §5. |

## 2. Libraries

| Library | Version | Declared `minSdk` in the AAR | Declared `minCompileSdk` |
|---|---|---|---|
| androidx.compose BOM | 2026.06.01 | — | — |
| ├ androidx.compose.ui:ui | 1.11.4 | 23 | 35 |
| ├ androidx.compose.foundation | 1.11.4 | 23 | 35 |
| ├ androidx.compose.material3 | 1.4.0 | 21 | 35 |
| └ androidx.compose.material:material-icons-core | 1.11.4 | (none) | (none) |
| androidx.core:core-ktx | 1.18.0 | 23 | 36 |
| androidx.activity:activity-compose | 1.12.4 | 23 | 36 |
| androidx.lifecycle:* | 2.10.0 | 23 | 35 |
| androidx.navigation:navigation-compose | 2.9.8 | 21 | 35 |
| androidx.compose.material3.adaptive:adaptive / -layout / -navigation | 1.2.0 | 21 | 35 |
| androidx.exifinterface:exifinterface | 1.4.2 | 21 | 34 |
| androidx.room:* | 2.8.5 | 23 | 34 |
| org.jetbrains.kotlinx:kotlinx-serialization-json | 1.11.0 | — | — |
| org.jetbrains.kotlinx:kotlinx-coroutines-* | 1.10.2 | — | — |
| io.coil-kt.coil3:coil-compose / coil-core | 3.4.0 | 23 | (none) |
| com.squareup.okio:okio | 3.17.0 | — | — |
| com.vanniktech:android-image-cropper | 4.7.0 | 21 | 1 |
| junit:junit | 4.13.2 | — | — |
| androidx.test:core / runner / rules | 1.7.0 | — | — |
| androidx.test.ext:junit | 1.3.0 | — | — |
| androidx.test.espresso:espresso-core | 3.7.0 | — | — |

Every `minSdk` figure above was read from the AAR's own
`META-INF/com/android/build/gradle/aar-metadata.properties`, not from release notes.

## 3. Versions that had to be lowered, and why

The design documents name Compose BOM 2026.08.00 and Room 2.8.5. Room 2.8.5 is in use
unchanged. The others were adjusted for measured reasons:

| Wanted | Problem found | Chosen instead |
|---|---|---|
| Compose BOM 2026.08.00 (Compose 1.12.0) | `ui-android:1.12.0` declares `minCompileSdk=37` and requires AGP 9.1+ | BOM 2026.06.01 (Compose 1.11.4, `minCompileSdk=35`) |
| core-ktx 1.19.0 | `minCompileSdk=37` | core-ktx 1.18.0 (`minCompileSdk=36`) |
| lifecycle 2.11.0 | requires AGP 9.1.0+ | lifecycle 2.10.0 |
| activity-compose 1.13.0 | `minCompileSdk=37` | activity-compose 1.12.4 |
| navigation-compose 2.10.1 | declares **`minSdk=24`** in its AAR, which would silently break the API 23 contract | navigation-compose 2.9.8 (`minSdk=21`) |
| Coil 3.6.2 | `minCompileSdk=37` | Coil 3.4.0 (no compileSdk floor) |
| Coil 3.5.0 | requires `kotlin-stdlib 2.4.0`, which the pinned Kotlin 2.2.21 compiler cannot read (it reads metadata up to 2.3.0), and there is no KSP release for Kotlin 2.4 yet | Coil 3.4.0 (requires stdlib 2.3.10) |

Moving to AGP 9.x would have pulled in Gradle 9.x, Kotlin 2.4 and a KSP release that
does not exist yet. The resulting stack would not build at all. Nothing was lost beyond
version numbers, and **minSdk stayed at 23**, which is the constraint that actually
matters.

## 4. Dependency conflicts encountered

1. **Kotlin metadata 2.4.0 vs compiler 2.2.21.** Coil 3.5.0 pulled
   `kotlin-stdlib:2.4.0` onto the classpath, and the compiler refused to read it
   (`expected version is 2.2.0 ... can read versions up to 2.3.0`). Resolved by
   pinning Coil 3.4.0 rather than by forcing a lower stdlib, which would have moved
   the failure to runtime.
2. **`org.jetbrains.compose.*` alias modules.** Coil's Compose module depends on the
   Compose Multiplatform coordinates, which are thin aliases that resolve to the
   AndroidX artifacts on Android. They add no duplicate code, and the resolved
   AndroidX versions are the ones in the table above.

## 5. Build and install evidence

| Check | Command | Result |
|---|---|---|
| Debug build | `./gradlew assembleDebug` | SUCCESS |
| Release build (R8) | `./gradlew assembleRelease` | SUCCESS, 2,432,762 bytes |
| From-scratch rebuild | `./gradlew clean` then `assembleDebug assembleRelease testDebugUnitTest --no-build-cache --rerun-tasks` | SUCCESS in 40s, **114 tasks executed, 0 from cache** |
| JVM unit tests | `./gradlew testDebugUnitTest` | 104 tests, 0 failures |
| Instrumented tests | `./gradlew connectedDebugAndroidTest` | 51 tests, 0 failures |
| Lint | `./gradlew lintDebug` | 0 errors, 56 warnings |
| APK produced (debug) | `app/build/outputs/apk/debug/app-debug.apk` | 14,727,286 bytes |
| APK minSdk (both variants) | `aapt2 dump badging` | `minSdkVersion:'23'` |
| APK targetSdk | `aapt2 dump badging` | `targetSdkVersion:'36'` |
| Native ABIs | `aapt2 dump badging` | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |
| Merged permissions | merged manifest | `CALL_PHONE`, `READ_CONTACTS` only, plus AndroidX's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` |
| Room schema | `app/schemas/…/1.json` | exported, version 1 |
| Release signing | `apksigner verify --print-certs` | v1 and v2 verified; **signer is `CN=Android Debug`**, i.e. a test build |
| Release certificate SHA-256 | | `0c7362790d5e5ebcd7bc17a494b94bdc95a6fada51a10dcf2f0974d9add5474f` |
| Release APK SHA-256 | | `1b85cdd1083f334738f2481bab2f79eee02340f4914aaa6caee784e505414f5e` |
| Installed on API 23 | `adb -s emulator-5554 install` | Success |
| Launched on API 23 | `am start` | ran with no `FATAL EXCEPTION` in logcat |

The two APK builds are not byte-identical to the previous run (a few bytes apart), which is
expected: the signing and packaging steps embed timestamps.

### Device / emulator used

| Target | Detail |
|---|---|
| API 23 emulator | `SilverPhone_API23`, `system-images;android-23;google_apis;arm64-v8a`, 1080x1920 @ 420 dpi |
| Physical device (available) | Android 16 / API 36, arm64-v8a (`ro.build.version.min_supported_target_sdk=28`) |

The 32-bit concern from the functional spec is addressed by the `armeabi-v7a` ABI being
present in the APK; it was **not** verified on a real 32-bit device, and that is recorded
as unverified rather than assumed.

## 6. Reproducing

```shell
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME=~/Library/Android/sdk
./gradlew assembleDebug testDebugUnitTest
./gradlew connectedDebugAndroidTest   # needs a running device or emulator
```

`local.properties` holds `sdk.dir` and is machine-specific; it is not part of the
product source.
