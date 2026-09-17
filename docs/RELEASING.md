# Releasing a new version

The app checks for updates by asking GitHub for the newest **published release**. A
tag that was only pushed is invisible to it, so a version only exists for the app once
the release job below has run.

## 1. Bump the version

In `app/build.gradle.kts`:

```kotlin
versionCode = 2        // +1 every release; the store and the phone compare this
versionName = "1.0.1"  // the number the About screen shows and compares
```

## 2. Make the documents true again

- `README.md` (Chinese, the default) and `README.en.md` (English): feature list,
  permissions, test counts. Both are written in their own language; neither is a
  translation of the other.
- `docs/spec/05_设计规格.md`: the acceptance-case list, if behaviour changed.
- `IMPLEMENTATION-NOTES.md`: anything that departs from `docs/spec/00`–`04`.

## 3. Verify

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug assembleRelease
```

`connectedDebugAndroidTest` needs a phone or emulator attached. Record the result,
including what was *not* run, in `acceptance-results.md`.

## 4. Commit, tag, push

```sh
git add -A
git commit -m "SilverPhone 1.0.1"
git push origin main
git tag v1.0.1
git push origin v1.0.1
```

## 5. What CI does with the tag

`.github/workflows/android.yml` builds and tests the tag, keeps both APKs as workflow
artifacts (`silverphone-release-apk`, `silverphone-debug-apk`), and then publishes a
GitHub release named `v1.0.1` with exactly one file:

| Asset | Source |
|---|---|
| `SilverPhone-v1.0.1.apk` | `app/build/outputs/apk/release/app-release.apk` |

The debug APK is deliberately **not** published: a release page is what a family
downloads from, and a second, similarly named package there only invites the wrong
choice. Take the debug build from the run's artifacts when it is needed for testing.

The release notes are generated from the commits since the previous tag.

## 6. Check it afterwards

```sh
gh run watch                     # the run for the tag push
gh release view v1.0.1 --web     # the assets the app will offer
curl -s https://api.github.com/repos/Changjingjiu/SilverPhone/releases/latest \
  | grep '"tag_name"'            # exactly what the About screen reads
```

On a phone with the previous version installed, open **Family settings → About →
Check for updates**: it must name `v1.0.1` and offer the download page.

## Signing, and why an update may refuse to install

The release build is currently signed with the **Android debug key** (see
`README.md`, "Known limitations"). That is fine for testing and for the phone that
first installs the app, but Android refuses to install an APK whose signature differs
from the installed one, so:

- a phone that has one of these releases installed must uninstall the app before
  installing the next one — which deletes the contacts stored in it, so export first;
- once a real signing key exists, every later release must use that same key, or the
  same uninstall-first rule applies forever.

Nothing in the app downloads or installs an APK by itself: the About screen only opens
the release page in the browser.
