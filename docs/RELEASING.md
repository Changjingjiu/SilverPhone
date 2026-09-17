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

- `README.md` (English, the default) and `README.zh-CN.md` (Chinese): feature list,
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

## 5. Publish the release

CI builds and tests the tag and keeps both APKs as workflow artifacts
(`silverphone-release-apk`, `silverphone-debug-apk`), but it does **not** publish a
release: the signing key is not in CI, and an unsigned or debug-signed APK on a release
page is worse than no release at all.

Publish from the machine that holds the key, using the APK built from the tag:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew clean assembleRelease

# gh does not rename an asset on upload: `path#label` only sets the label shown on the
# page, and the file keeps its own name. Copy it to the name the release should carry.
cp app/build/outputs/apk/release/app-release.apk /tmp/SilverPhone-v1.0.2.apk

gh release create v1.0.2 /tmp/SilverPhone-v1.0.2.apk \
  --verify-tag \
  --title "SilverPhone v1.0.2" \
  --generate-notes \
  --notes "Signed with the SilverPhone release key (SHA-256 DF:EA:77:...:F4). A build signed with the debug key cannot be installed over: uninstall it first, which deletes the contacts inside, so export them from 家属设置 → 导入 / 导出 first."
```

One asset, the release APK. The debug APK stays in the build job's artifacts: a release
page is what a family downloads from, and a second, similarly named package there only
invites the wrong choice.

`--generate-notes` lists the commits since the previous tag; `--notes` is the paragraph
that matters to someone installing it. Both are shown, so keep the second one short and
factual.

## 6. Check it afterwards

```sh
gh run watch                     # the run for the tag push
gh release view v1.0.2 --web     # the assets the app will offer
curl -s https://api.github.com/repos/Changjingjiu/SilverPhone/releases/latest \
  | grep '"tag_name"'            # exactly what the About screen reads
```

On a phone with the previous version installed, open **Family settings → About →
Check for updates**: it must name `v1.0.2` and offer the download page.

## Signing

Since v1.0.2 the release build is signed with a real key. The three pieces:

| | |
|---|---|
| Keystore | `~/keystores/silverphone-release.jks` — **outside the repository** |
| Alias | `silverphone`, RSA 4096, valid 10000 days |
| Certificate SHA-256 | `DF:EA:77:57:C2:00:96:19:0D:3F:26:EB:53:02:67:6A:95:EC:A4:E5:FE:9E:D4:FA:6D:27:5D:6B:72:81:52:F4` |
| Credentials | `keystore.properties` in the repository root, git-ignored |

**Back both up.** Losing the keystore means this app can never be updated again: Android
refuses to install an APK signed with a different key over an existing one, so the only
way forward would be a new package name and everyone reinstalling from scratch.

Builds without `keystore.properties` — a fresh clone, or CI — still compile, but their
release APK is signed with the debug key and `assembleRelease` says so in a warning.
Never publish one of those.

### v1.0.1 and earlier

Those releases were signed with the Android debug key, which is a different key. A phone
that has one of them installed must uninstall the app before installing v1.0.2 or later —
which deletes the contacts stored in it, so use **家属设置 → 导入 / 导出** to export them
first, and import the file after reinstalling. From v1.0.2 onward, updates install
normally over each other.

Nothing in the app downloads or installs an APK by itself: the About screen only opens
the release page in the browser.
