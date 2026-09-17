# Acceptance results

Recorded: 2026-09-17. Every line states **what was actually run**, on which target, and
what was **not** run. Status values are only 通过 (pass) / 失败 (fail) / 未验证 (not
verified). Reproducing a build is not evidence that a real phone call works.

## v1.0.1 release verification (2026-09-17)

- Version: `1.0.1` / version code `2`. No application behaviour changes since
  `v1.0.0`; this release includes README, screenshot and specification-path cleanup.
- JDK 17: `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`
  completed successfully. Gradle reused up-to-date outputs; the unit-test report
  contains 104 tests, 0 failures, 0 errors and 0 skipped tests.
- No device or emulator was connected during this release check, so instrumented
  tests and installation were not rerun. The 51-test result below is earlier evidence.
- Release APK remains debug-signed and is a test distribution. Update compatibility
  depends on matching signing certificates; real-device calling remains unverified.

## 0. Test and build summary

| Suite | Command | Target | Result |
|---|---|---|---|
| JVM unit tests | `./gradlew testDebugUnitTest` | JDK 17, host | **104 tests, 0 failures** |
| Instrumented tests | `./gradlew connectedDebugAndroidTest` | API 23 emulator, arm64 | **51 tests, 0 failures** |
| Lint | `./gradlew lintDebug` | host | **0 errors, 52 warnings** |
| Debug build | `./gradlew assembleDebug` | — | SUCCESS |
| Release build (R8) | `./gradlew assembleRelease` | — | SUCCESS |

Unit tests by class: `DisplayNameRulesTest` 8, `PhoneNumberRulesTest` 12,
`ImportPlannerTest` 8, `DialCoordinatorTest` 11, `BackupJsonTest` 9, `ContactSearchTest` 13,
`ContactsImportPlannerTest` 11, `CountryCodeTest` 12, `AppVersionTest` 8,
`GitHubReleaseJsonTest` 5, `AboutViewModelTest` 7.

Instrumented by class: `ContactRepositoryTest` 15, `HomeDialTest` 8,
`BackupRoundTripTest` 14, `PhotoNormalizerTest` 7, `ManageSearchFocusTest` 4,
`AboutScreenTest` 3.

### Targets actually used

| Target | Detail | Used for |
|---|---|---|
| API 23 emulator | `SilverPhone_API23`, `system-images;android-23;google_apis;arm64-v8a`, 1080x1920 @ 420 dpi, Android 6.0 | All instrumented tests, install, launch, navigation, create/save, export, permission path |
| Physical device | Android 16 / API 36, arm64-v8a | **Not used.** No test in this round ran on a real phone. |

### Bugs found by these tests

Two rounds of review found real defects. The second round was driven by the project owner
using a build on a real phone, which is where the two most serious ones surfaced.

**Round 1 — found by the automated tests and by reading the code**

1. **The dial lock never opened.** `lastAcceptedAt` was initialised to `Long.MIN_VALUE`,
   so the first `now - lastAcceptedAt` overflowed to a negative number and every first
   tap was refused. Found by `DialCoordinatorTest.oneTapProducesExactlyOneRequest`.
2. **`isAllowedEntry` accepted nested paths** (`photos/a/b.jpg`), letting an entry
   through the whitelist that the protocol does not define. Found by
   `BackupJsonTest.onlyTheTwoDocumentedEntryShapesAreAllowed`.
3. **Opening a contact and going back always asked "放弃这次修改？"** even with no
   edits, because the dirty check tested "is a field non-empty" instead of "does the
   draft differ from what is stored".
4. **A placeholder-only contact list required an image loader.** `ContactPhoto` read the
   loader before checking whether a photo existed. Found by `HomeDialTest`.
5. **The system-contacts import had no reachable action.** `PickBody`'s own weighted list
   consumed the whole content height, so the action bar holding 下一步：预览 was laid out at
   zero height. With an address book full of contacts there was literally no way to proceed
   — the emulator had an empty address book, which takes a different layout branch, so the
   automated pass missed it. Reported by the owner.
6. **Search matched everything for a query without digits**, because the digit-stripped
   needle was empty and `contains("")` is true for every number. The "no results" state was
   unreachable.
7. **A non-square or oversized archived photo was silently cropped and accepted**, because
   the shape was checked after normalisation, and normalisation centre-crops. The check now
   runs on the encoded image.
8. **A rejected photo was reported as "space is insufficient"** — the normaliser's reason
   was discarded, so a HEIC or a 24 MP photo told the family to free up storage.
9. Tapping 使用照片 while the crop image was still loading reported a failure and disabled
   the button permanently; system back during the crop step discarded the whole editor.
10. The family settings screen and the home screen had no window insets; the home retry
    button was a no-op; the export screen kept a dead status callback.

**Round 2 — found by the owner using a real phone**

11. **The dial never worked with the permission granted.** `AndroidPhoneLauncher` started
    the call from the *application* context, which Android refuses without
    `FLAG_ACTIVITY_NEW_TASK`; the resulting `AndroidRuntimeException` was caught and mapped
    to "电话没有打开，请再试一次". Every real tap produced that message and no call. The
    emulator pass had only covered the missing-permission path. Fixed by starting the call
    from the foreground Activity and separating the permission probe from the launch.
    Re-verified on the emulator: tapping a card now brings up the system's
    `InCallActivity` with a running call timer.
12. **System back on the dial-help screen exited to the phone's home screen.** The help is
    an overlay inside the home destination, so back popped the only entry in the graph and
    the app finished. A `BackHandler` now closes the help.

**Round 3 — a full three-way audit of logic, maintainability and UI**

Two independent reviews plus my own pass. Every finding below was reproduced before it was
changed, and the arithmetic-checkable ones were verified numerically.

13. **A clock change could block every call.** The dial lock measured the interval between
    taps with `System.currentTimeMillis`. A wall clock that moves backwards - time sync
    after the phone has been off, a timezone change, the user correcting the date - makes
    the subtraction negative, so the check reads "too soon" forever and *every* tap on
    *every* card is silently ignored until the clock catches up. Now uses elapsed real time,
    and the parameter is required so no caller can forget.
14. **A 4000x1000 photo was stored at 250x250 instead of 512x512.** The decoder sampled on
    the longest edge while the crop keeps the shortest, so any photo wider than about 2:1
    (panorama, 21:9, a screenshot) lost three quarters of its pixels. Verified numerically
    before and after; `PhotoNormalizerTest` now pins the behaviour for wide, ultra-wide,
    tall, square and small sources.
15. **The editor's colour swatches overflowed their row.** Six 56 dp swatches with 12 dp
    gaps need 396 dp and a 411 dp phone offers 355 dp, so the sixth was laid out as a
    39x56 dp ellipse below the minimum touch size and was effectively unselectable. Now two
    rows of three.
16. **The system-contacts preview collapsed at the larger presets.** A non-scrollable
    header plus a weighted list means the list gets the leftover height, which is zero once
    the header fills the screen - the same defect already fixed for the file-import
    preview, not applied here. Now one list.
17. **The pick row's 修改 button was squeezed out.** The text column has 231 dp beside the
    checkbox and thumbnail; two labelled buttons need about 240 dp, so at 大 and above the
    rename action collapsed. The actions are now stacked.
18. **Dialogs could not scroll**, so a contact with several numbers at 超大 pushed the later
    numbers and the 取消 button off the dialog.
19. **Disabled buttons were unreadable: white on #CBD5E1 measures 1.48:1.** For a
    low-vision user the label simply was not there. Now 6.15:1 with the muted fill carrying
    the "not now" signal.
20. **`CompactActionButton` cut its label mid-glyph** (`maxLines = 1` with clip) at 特大 and
    above; it wraps now.
21. **The font screen's 保存 and 取消 wrapped to one character per line** at 超大, because
    each half-width button had 59 dp for a 77 dp label. Stacked full width, like every
    other screen; verified on the device (保存 y=1400..1473, 取消 y=1621..1694).
22. **Selection was signalled by colour alone** in the font presets and the import mode
    options, with no selected state exposed to assistive technology.
23. **The contacts import used the archive wording** ("加上这个文件会超过…", "文件无法导入")
    on a path where no file was ever chosen, blamed an over-long name for an unusable
    number, and left no way to fix that number. All three corrected.
24. **S07 offered no explicit way back** in its permission, loading and denied states.
25. **The editor stayed editable during a save**, and an edit made in that window was
    silently discarded when the screen closed on success. The fields and photo actions are
    now locked while saving.
26. **Search returned contacts the query never matched.** A mixed query such as "老伴2" was
    stripped to "2" and matched any number containing a 2. Only queries made entirely of
    number characters consult the number column now. (The test for this also exposed that
    `+86138…` did not find a number stored as `86138…`; the prefix is now ignored.)
27. **Cancelling an export mid-copy left a truncated ZIP at the user's chosen location**,
    which the family could then send and the other phone would reject. It is cleaned up
    before the cancellation propagates.
28. **Every accepted archive kept its staged bytes on disk** (up to ~65 MiB) for the rest of
    the session, because the staging directory was exposed on the result but never read by
    any consumer. It is now removed on both paths and the field is gone.
29. Smaller corrections: the manage list no longer flashes "还没有亲人" before its first
    database read; dialog actions meet the app's own 56 dp minimum; the destructive
    "confirm replace" button uses a bin rather than a refresh-arrows glyph; the
    system-contacts number types are resources rather than Chinese literals in a platform
    class; the editor no longer prints the same phone example twice; the name dialog's hint
    is replaced by the error while the field is in error; the contacts result message now
    accounts for entries dropped at commit; and five strings left over from the removed
    gate screen were deleted.

Two further issues were found by lint and fixed: a Flow pipeline being constructed
inside composition (`MainActivity`), and a missing
`<uses-feature android:name="android.hardware.telephony" required="false"/>` that would
have had the store filter the app off devices without a telephony stack.

### Round 4 (2026-09-17, same day): the owner's second list, and what the device said

The owner reviewed a second build on the phone and reported five items, plus one bug.
All six were reproduced before anything was changed; two of the six turned out to be
worse than reported once the device was checked.

| # | Reported | Reproduced as | Fixed by |
|---|---|---|---|
| 30 | The English home layout is wrong | Confirmed: at the old base sizes the header fell into its stacked form, leaving a lone pill under a large title with dead space to its right | The header is now always one row with the family entry pinned to the trailing edge; the title takes the remaining width and wraps. Verified in both languages and at the largest preset. |
| 31 | Standard must be the normal size a young person reads | Confirmed: the standard preset was 22 sp body / 32 sp title, well above the platform's 16 sp / 28 sp | Rebases the five roles to 28 / 22 / 16 / 16 / 14 sp; the four ratios are unchanged. |
| 32 | A country code has to be settable | Confirmed missing | New setting + live "will dial" preview. **Verified on the device**: with `+86` the dialler received 14 characters (`+8613800138000`); after changing to `+1` it received 13 (`+113800138000`). The count is read from `dumpsys telecom`, whose redaction preserves length. |
| 33 | The interface has to be switchable to English | **Worse than reported.** The choice was stored but never applied at startup, and Save only applied it when the value changed - so the app came up in the system language on every launch and re-saving did nothing | `App.applyStoredLanguage` applies the stored tag before the first Activity attaches; Save now applies on every press. Verified by cold start: with `zh` stored, the app came up in Chinese. |
| 34 | The permission dialog must appear on the press, not on another page | Fixed, but the first fix drew the help overlay *behind* the dialog | `awaitingPermission` holds the overlay back until the dialog is answered. Verified on the device: the dialog is over the home screen with no help behind it, and pressing Allow placed the call without a second tap. |
| 35 | 管理亲人: typing in the search box made the two bottom buttons disappear, with no way back | Confirmed. This was a regression I had introduced myself by hiding the action bar while the field had focus | The bar is unconditional again, and a tap anywhere else clears focus. Covered by `ManageSearchFocusTest`, which fails if the bar ever becomes conditional again. |

Not defects, but recorded because they cost time and would mislead a future reader:

- **A database that appeared to wipe itself.** The stored contacts and language vanished
  between two checks. `dumpsys package` settled it: `firstInstallTime` matched the moment
  in question, and the code path was `/data/app/…-2`, so the package had been
  **uninstalled and reinstalled** by the tooling - which clears app data. The app has no
  code path that deletes user data: there is no `fallbackToDestructiveMigration`, and
  `TransferDirs.cleanUp` only touches cache subdirectories. Repeating the install and the
  permission revoke explicitly left the data intact.
- **Tapping outside the search field does not move focus on API 23.** Measured with
  `onFocusChanged`, the field is cleared and immediately re-focused, because hiding the
  IME makes the window regain focus. Current Android does not do this. The regression test
  asserts the behaviour the screen owns instead; see IMPLEMENTATION-NOTES.md.
- **The emulator hung mid-session** and had to be restarted. After the restart the seeded
  contacts and the stored language were still there.

### Round 4, release build (R8) verification

R8 minification and resource shrinking can break reflection-shaped code, and the archive
path is exactly that shape: Room, and kotlinx.serialization with strict parsing. The
release APK was therefore installed and driven on the API 23 emulator, not just built.

| Step | Result |
|---|---|
| `adb install -r app/build/outputs/apk/release/app-release.apk` | Success; package `com.silverphone.app`, `minSdkVersion=23` read back from the APK's manifest with `aapt2 dump xmltree` |
| Launch, home screen | Renders; Room opens the database it created |
| Create a contact through the editor | Saved; the management list shows `Daughter / 13800138000` |
| Export contacts | `This exports 1 contacts and 0 photos`; the file is written |
| Manifest inside the produced ZIP | `format: silverphone-backup`, `schemaVersion: 1`, a UUID id, the name, the number, `sortOrder`, `placeholderColor`, `photo: null` — the strict writer works under R8 |
| Import that same file back | The preview parsed it and reported the export timestamp and counts, and detected the duplicate as `0 will be added, 1 kept, 1 skipped` |
| Crash log | No `FATAL EXCEPTION`, no `SerializationException`, no `ClassNotFoundException` at any step |

Two defects were found by doing this and fixed:

- The English management row wrapped: `Move up` fitted a third of the width and
  `Move down` did not, so the row had two one-line labels and one two-line label. The
  English labels are now `Up` / `Down` (the arrows above them carry the meaning); the
  Chinese labels 上移 / 下移 already fitted.
- The exported file was always named `SilverPhone_联系人_…`, even in the English interface.
  The prefix is now a string resource, and an English build exports
  `SilverPhone_contacts_20260917_140342.zip` — read back from `/sdcard/Download` on the
  device. Only the `.zip` extension is part of the format.

### Round 5 (2026-09-17): the pre-release review

Three reviewers read the whole codebase in parallel — one on `domain/` and `data/`, one on
`platform/` and the Application, one on `ui/` and the resources — and their findings were
reproduced on the device before anything was changed. Two of the findings were confirmed as
release blockers and fixed; the rest are listed with what was done about them.

| # | Found | Reproduced | Fixed by |
|---|---|---|---|
| 36 | **A landline could never be reached.** The dialling code was concatenated onto a number that already carried a national trunk prefix: `01012345678` with the default `+86` was dialled as `+8601012345678`, which the network rejects, and the failure was silent. Reachable through the ordinary flow: address-book import → landline → the green button. | Run on the device: the editor's own preview read `拨打时会拨出 +8601012345678`. | One leading `0` is now dropped before the code is added — that zero is the domestic trunk prefix the code replaces. Italy is the exception (`+39 06 …` keeps it) and is named in the code. Unit tests cover a Beijing, a Shanghai and a UK landline, the Italian case, and an already-international number. Verified on the device: the same preview now reads `+861012345678`. |
| 37 | **A failing database read crashed the app on the primary action, and left the state stuck.** The tap handler read the contact and the dialling code in an app-scoped coroutine with no try/catch. A throw killed the process (no `CoroutineExceptionHandler` anywhere), and had it been contained the state would have stayed at `Checking`, which disables every card with no path back to `Ready`. | Read from the code; the home list already models the same failure (`ContactsLoad.Failed`), so the dial path was the odd one out. | The whole body is wrapped: a failure becomes a new `DialProblem.StorageUnavailable`, shown as its own help screen ("暂时读不到亲人信息"), which also releases the latch. A reading of the dialling code that fails now dials exactly what is stored rather than guessing a country. A `CoroutineExceptionHandler` on the application scope is the net under everything else. |
| 38 | **The permission dialog could silently throw the call away.** The resumed half of a tap was re-issued through the 1 s spacing guard, so answering the system dialog quickly — inside that window — produced no call, no message, and the need to tap again. | From the code: `onContactTapped` sets the timestamp, `onCallPermissionResult(true)` re-requests immediately. | `requestDial(resumed = true)` for the second half of a tap the user already made. The guard stays for genuinely new taps. |
| 39 | **In landscape the Call button was off screen**, at the standard size, on the one screen the app exists for: the photo is a square as wide as its card, and in a short window that square is taller than the space left for the name and the button. | Measured on the device in landscape: only the title and the family entry were inside the display. | The grid bounds the photo by the space the card's name and button need, with a 96 dp floor below which the card scrolls instead of shrinking. Verified: in landscape at the standard size the Call button is now fully inside the display, and portrait is unchanged because the cap never binds there. |
| 40 | A failed contacts read is reported as "no contacts in the address book" | From the code: `SecurityException` returned `emptyList()` and the caller marked the permission as granted, so the family saw a wrong, unfixable screen. Other provider failures escaped and crashed. | `SystemContactsRead` with `Loaded` / `NotPermitted` / `Failed`; the permission state goes back to the one that offers to ask again, and a genuine failure has its own message. |
| 41 | Three preference writers threw instead of reporting a result, against the repository's own contract | From the code; a failed write on the text-size or language screen killed the process. | They return a Boolean. Both screens stay put and say the save failed instead of dismissing as though it had worked. |
| 42 | Controls below the app's own 56 dp touch floor: 全选 / 清除, 选择要拨打的号码, 修改, the preview's 返回, and the search-clear button — Material's defaults are 40 dp and 48 dp. | From the code. | All five take `minTouchTarget`. |
| 43 | The call glyph on a card did not grow with the text preset, unlike every other button's, because `heightIn` on an `ImageVector` only enlarges the box around its intrinsic 24 dp size. | From the code. | `size(primaryGlyph)`, as the other buttons do. |
| 44 | The crop library's provider exposes the whole cache — including the export archives and the import staging area — plus the whole files dir and the external files dir, and ships an exported activity. The app tells itself only `cacheDir/exports/` is reachable. | Read from the **merged release manifest**, not from this repository's manifest, which was the point: the library contributes them. No URI is ever handed out today (`saveUri = null`), so this is a surface, not a demonstrated leak. | The provider's path map is overridden to serve nothing, and the activity is re-declared `exported="false"` through `tools:replace`. Confirmed in the rebuilt APK's manifest. |
| 45 | Recycled a bitmap the crop view may still be drawing: `centerSquare` returns the caller's bitmap when it is already square, and the encode path recycled it either way. | Read from the code. | Only bitmaps this call created are recycled. |
| 46 | The import staging directory outlived a cancelled preview, up to the whole expanded archive. | Read from the code: the delete ran after the call, not in a `finally`, so a cancellation at a suspension point skipped it. | Moved into `finally`. |
| 47 | The search did not fold full-width digits, so `１３８` reported no results for a number that is on the phone, while the save path folds exactly those characters. | Reproduced in a unit test. | The query is NFKC-folded before matching, like a saved number. Two tests cover digits and letters. |
| 48 | Dead strings (`font_preview_title`, `preferences_language_zh/en`) and two comments pointing at the wrong spec sections. | From the code. | Removed and corrected. |
| 49 | `transferDirs.cleanUp()` ran recursive deletes on the main thread during `Application.onCreate`. | From the code. | Moved to a background coroutine; nothing about it has to finish before the first frame. |
| 50 | On API 33+ a language chosen in Android's own settings was silently reverted at every launch, because the stored tag was applied unconditionally. | From the code and the platform rule. | The stored tag is applied only when the platform has no per-app locale of its own. |

**Checked and found sound.** The reviewers found no way to lose or corrupt contacts: every
write goes through one mutex and one transaction, the revision is read and incremented
inside the same transaction as the change, `MIGRATION_1_2` passes Room's post-migration
validation (checked against Room 2.8.5's column comparison, including the default-value
rule), and a failed import rolls back entirely. The archive reader rejected every malformed
file they constructed — traversal, nested names, undeclared entries, count and byte limits
counted from bytes actually read rather than declared sizes. The dial lock was confirmed to
issue at most one request per tap across recomposition, rotation and Activity recreation.
No unguarded privileged call was found.

**Judged and not changed.** A limit-legal archive (500 contacts × 128 KiB photos) is held
in memory during the import preview, so a maximal archive could exhaust the heap on a small
device. Real families hold a handful of relatives, the reader verifies every photo before it
is decoded, and the failure is a restart rather than data loss, so the fix (staging that
survives until the commit) is recorded here as a known limit rather than taken on now. A
process killed mid-export can leave a partial ZIP in the cache until the next launch more
than 24 h later; the export screen never offers it. At the largest text preset the option
list on the text-size screen shows two of its four choices and scrolls — the two that make
the text *smaller* are the ones immediately visible, so the size can always be lowered, and
the same holds with the system font scale raised to 1.3. The reviewers' arithmetic that the
list could collapse to zero height does not bite at any combination reachable on the test
device, but it is a consequence of the preview being pinned, and the pinning is the fix for
the sliced preview reported earlier.

### Round 6 (2026-09-17): the About screen, the update check and the first release

The owner asked for two more things, in their words:

> 1.要在软件的设置里最下面放上一个关于按钮 点开之后现实软件的版本 github项目的地址(要可以
> 点击) 检查更新 … 并附属上一些隐私政策
> 2. 仓库如图2所示 记得要搞好能同步更新的功能 另外整个项目都叫 SilverPhone 不要命名混乱!

**The rename, in full.** Every place a name appears was renamed in one pass instead of
being left behind as a compatibility layer: the Gradle root project, the `namespace` and
`applicationId` (`com.silverphone.app`), therefore every package, directory and import, the
theme (`Theme.SilverPhone`), the Room database file (`silverphone.db`) and its schema
directory, the backup archive's `format` marker and file prefix, the fixture archive
(`SilverPhone_联系人_测试包.zip`), the launcher label in both locales, and the repository
itself. `rg -i 'familydialer|family dialer|family-dialer|亲人电话'` over the tree now matches
only `IMPLEMENTATION-NOTES.md`'s own account of the rename. A build that carries the old
name is not upgraded in place: the database file changed name with the package, and that is
the intended reading of the project's no-compatibility-layer rule, stated here so nobody
looks for a migration that does not exist.

What the new screen cost, and what was found while building it:

| # | Found | Reproduced | Fixed by |
|---|---|---|---|
| 51 | **"It declares no `INTERNET` permission at all" stopped being true the moment an update check was asked for.** The claim was in both READMEs, and the manifest's own comment listed the permissions the app does *not* have. A reader who opened the merged manifest would have caught the contradiction before a reviewer did. | From the request itself: a version check that cannot reach the network cannot exist. | The manifest declares `INTERNET` with a comment naming its single use, the READMEs were rewritten, the permission table row `INTERNET 否` in `docs/spec/04` is recorded as overridden in `IMPLEMENTATION-NOTES.md`, and the privacy text on the About screen states the same limit. |
| 52 | A release tag compared as text sorts before it compares as a version: `v1.10.0` sorts before `v1.9.0`, so the screen would have called a months-old release the newest one. | Written as unit tests before the comparison was used anywhere. | `AppVersion` compares components as numbers, and treats a pre-release suffix - the debug build ships as `1.0.0-debug` - as older than the release with the same number. |
| 53 | **The instrumented suite went red twice in a row** while the API 23 AVD was still finishing a cold first boot: once as a failed `AboutScreenTest` assertion, once as a killed instrumentation process during `PhotoNormalizerTest`. Both runs named a different test, and both runs reported one failure with the process dying immediately afterwards. | Kept because a red run that is explained away is worth less than one that is explained: the same suite then passed 51/51 twice on the booted emulator, and `AboutScreenTest` passed 3/3 on its own in between. | Nothing in the app. The run of record is on a device whose boot animation has stopped; the two red runs are recorded here so the sequence is not hidden. |

#### The release itself

The workflow took three tagged runs to publish a release. The two failures are recorded
because the third one is only evidence if the first two are visible:

| Run | Result | What failed | Fix |
|---|---|---|---|
| `35206887053` (`v1.0.0`) | build ✓, release ✗ | A two-path artifact upload stores the APKs as `release/app-release.apk` and `debug/app-debug.apk`, so the release job's `cp apks/app-release.apk` found nothing. | One artifact per APK. |
| `35207653502` (`v1.0.0`) | build ✓, release ✗ | `gh release create --generate-notes` reads the tag history and the release job had never checked the repository out: `fatal: not a git repository`. | `actions/checkout` with `fetch-depth: 0` in the release job. |
| `35207809791` (`v1.0.0`) | **success** | — | — |

The same workflow passed on `main` (`35207800394`) on a clean GitHub runner, which is
evidence that `testDebugUnitTest`, `lintDebug`, `assembleDebug` and `assembleRelease` do
not depend on this machine's SDK installation or Gradle cache.

The published release:

| Item | Value |
|---|---|
| Release | `v1.0.0`, marked Latest, published 2026-09-17 |
| Asset | `SilverPhone-v1.0.0.apk` (2,694,130 bytes), signed with a debug key, so a test build. The debug APK was published with it at first and was then removed on the owner's instruction: a release page should carry one file, and the debug build stays in the run's artifacts. |
| What the app reads | `GET https://api.github.com/repos/Changjingjiu/SilverPhone/releases/latest` → `tag_name: v1.0.0`, `html_url: .../releases/tag/v1.0.0` |

#### The update check, end to end, on the emulator

Installed from the APK downloaded **from that release**, not from a local build
(`shasum -a 256` → `9085db5da47dd23ccfca2645b6a3077b529eac7d716bba3c85fb7e8789bf370e`):

| When | Installed build | What the screen said |
|---|---|---|
| Before any release existed | `1.0.0-debug` | `目前还没有发布可以下载的版本。` with a button to the releases page — the 404 path, seen on the device rather than only in a unit test |
| After `v1.0.0` was published | `1.0.0` | `已经是最新版本。` |

`design/screenshots/05-family-settings-zh.png` (About is the seventh and last row),
`design/screenshots/14-about-zh.png` (version, GitHub address, check button, privacy) and
`design/screenshots/15-about-update-check-zh.png` (the answer after a real check) are from
that run.

One thing the device said, which the release procedure now states as observed rather than
assumed: installing the release APK over an earlier build of the same package that had
been signed with a *different* debug key failed with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`,
and the older build had to be uninstalled first. Nothing in the app downloads or installs
an APK by itself; the check only opens the release page.

## 1. Core acceptance criteria

| # | Covers | What was done | Status | Evidence |
|---|---|---|---|---|
| AC01 | F01 | Icon assets generated from one geometry (bitmap densities + adaptive foreground + monochrome). APK built and installed on API 23. | 通过 (资源与安装) / 未验证 (桌面观感) | `design/icon-previews/`, `app/src/main/res/mipmap-*`. **The launcher's own rendering was not visually inspected on a target phone, and no comparison was made against other apps on that phone.** |
| AC02 | F02 | Fresh install launched: real empty state, no sample contacts, no login, no ads, family entry reachable. | 通过 | `design/screenshots/01-home-empty.png` |
| AC03 | F05 | Added "Daughter / 00000000099" through the editor UI, saved, force-stopped the process, relaunched. | 通过 | `03-home-after-restart.png`; DB read back shows `5\|Daughter\|00000000099` appended at sortOrder 5 |
| AC04 | F03 | Card is one accessibility node; a tap on it produces one request with the card's number. | 通过 (自动化) | `HomeDialTest.oneTapOnTheCardProducesExactlyOneCallRequest`, `theCardIsExactlyOneAccessibilityNode` |
| AC05 | F03 | 11 unit tests: 10 rapid taps → 1 request, second contact refused while in flight, host re-claim refused, late outcome ignored. UI-level: cards are disabled while a request is in flight. | 通过 (连点) / 未验证 (设备旋转) | `DialCoordinatorTest`, `HomeDialTest.whileARequestIsInFlightTheCardsAcceptNoFurtherTaps`. **No on-device rotation was performed.** |
| AC06 | F03, F13 | Both permission paths on the emulator. **Not granted:** tapping a card showed the short help screen, no call intent appeared in logcat, and back returned home. **Granted:** tapping a card brought up the system `InCallActivity` (`com.android.dialer/com.android.incallui.InCallActivity`) with a running call timer; hanging up returned to the app with the contacts list intact and no redial. | 通过 | `08-dial-help-no-permission.png`; `adb shell dumpsys activity activities` showing `InCallActivity`; `logcat` had no `FATAL EXCEPTION` |
| AC07 | F03 | Granting the permission does not dial on its own; a new tap is required. | 通过 (单元) | `DialCoordinatorTest.grantingPermissionDoesNotDialOnItsOwn` |
| AC08 | F03 | Returning to the foreground releases the lock without redialling. Order and scroll are driven by the stored `sortOrder`, which never changes on return. | 通过 (锁) / 部分 (真实通话返回) | `DialCoordinatorTest.returningToTheForegroundReleasesTheLatchWithoutRedialling`; hanging up on the emulator returned to the contact list with no redial. **No call on a real phone or over a real network.** |
| AC09 | F04 | Daily browsing cannot reach the editor: nothing on a card opens management; three taps are needed to reach settings. | 通过 | `HomeDialTest`, `design/screenshots/11-family-gate.png` |
| AC10 | F05 | Edit keeps id and position; cancel changes nothing; delete cascades to the photo and compacts the order; delete asks first. | 通过 (存储) / 部分 (UI) | `ContactRepositoryTest` (4 tests). The delete confirmation dialog was not exercised end-to-end on the device. |
| AC11 | F06 | Photos render from the Room blob with no source file present: the fixture photos were imported into the database with no album file on the device at all. | 通过 | `02-home-contacts.png` |
| AC12 | F07 | Move swaps neighbours, does nothing at the ends, order persists; searching filters only the management list. | 通过 (存储) | `ContactRepositoryTest.movingSwapsNeighboursAndDoesNothingAtTheEnds` |
| AC13 | F08 | One decided number per person. Exercised on an emulator address book seeded with a contact holding two numbers and no primary mark: the import refused to proceed and the preview named the person as needing a decision. | 通过 (合成通讯录) | Seeded via `content insert`; preview showed "还有 2 位需要先处理…" with 开始导入 disabled. **Not run against a real phone's address book.** |
| AC14 | F08 | System contacts import end to end, on an emulator address book seeded with a single-number contact, a two-number contact, and a 13-character name. Selected a valid person, previewed, imported. | 通过 (合成通讯录) | Result "已新增 1 位亲人，跳过 0 位"; the database then held `0\|女儿\|13800138000`. **Synthetic address book, not a real one.** |
| AC15 | F09–F11 | Real export produced by the app, then fed back into the app's real reader: 5 contacts / 3 photos, order, names, numbers, colours and decodable photos all preserved. | 通过 (同机往返) / 未验证 (两台真机) | `BackupRoundTripTest.whatTheWriterProducesTheReaderAcceptsUnchanged`; the archive the app produced on the emulator was also pulled and its manifest checked field by field against the database |
| AC16 | F11 | A different phone's own wording and photos survive an append. | 通过 (规则) | `ImportPlannerTest.sameIdIsSkippedSoTheTargetsOwnNameSurvives`, `ContactRepositoryTest.appendingTheSameArchiveTwiceAddsNothingTheSecondTime` |
| AC17 | F11 | Re-importing the same archive adds nothing the second time. | 通过 | `ImportPlannerTest.reimportingTheSameArchiveAddsNothing`, `ContactRepositoryTest.appendingTheSameArchiveTwiceAddsNothingTheSecondTime` |
| AC18 | F11 | Replace shows the exact counts and asks first. | 部分 | Counts are computed by `ImportPlanner.planReplace` and rendered in `ReplaceConfirmation`; **the dialog was not driven on the device.** |
| AC19 | F11 | A failure in the middle of a replace/append leaves the old contacts and photos intact. | 通过 | `ContactRepositoryTest.aFailedCommitRollsBackCompletely` (injects a primary-key collision mid-transaction, then asserts count, revision, name and photo digest are all unchanged) |
| AC20 | F11 | Broken archives are refused whole: corrupt photo bytes, missing declared photo, extra entry, `../` traversal, unknown schema version, unknown manifest field, sparse sortOrder, not-a-zip. | 通过 | `BackupRoundTripTest` (8 tests) + `BackupJsonTest` (9 tests) |
| AC21 | F09, F10 | Save, share and cancellation are reported at their real level ("已打开分享" is not "已送达"); cancelling the system picker is not a failure and does not touch contacts. | 部分 | Distinct statuses implemented; **the save/share/cancel flows were not driven on the device.** |
| AC22 | F12 | Four presets on a real device: selected 超大, the whole page re-rendered at the previewed size, saved, and the new size applied globally on another screen and survived a process restart. The stored `fontPreset` is `HUGE` and `contactsRevision` stayed at 1, so a font change does not look like a contact change. | 通过 (保存与全局生效) / 未验证 (取消恢复) | `13-font-huge.png` shows the page at 1.6× with 保存/取消 still reachable; `14-home-huge-single-column.png` shows the home screen at the same preset; the database was read back as `HUGE\|1`. **Cancel-restores was not exercised.** |
| AC23 | F12, F15 | At the app's largest preset the home screen reflowed from two columns to one by itself, with no truncated name and no shrunk text — the column rule measures four characters in the current style and falls back to a single column when two would not fit. Button labels wrap to two lines rather than shrinking. | 通过 (应用超大档) / 未验证 (系统最大字) | `14-home-huge-single-column.png`, `13-font-huge.png`. **The system font scale was left at 1.0 throughout; the combination with a 2.0 system scale was not measured.** |
| AC24 | F15 | Each card is announced once and is activatable. | 部分 (结构) / 未验证 (TalkBack) | `HomeDialTest.theCardIsExactlyOneAccessibilityNode` asserts exactly one node and that the name is not also exposed as separate text. **TalkBack itself was not run.** |
| AC25 | F14 | Works with no network. | 通过 (结构) / 未验证 (飞行模式) | Everything except the About screen's update check is local, and the check is started by a tap: with no connection it reports an unreadable answer rather than "you are up to date" (`GitHubReleaseJsonTest`, `AboutViewModelTest`). The merged manifest declares `CALL_PHONE`, `READ_CONTACTS` and `INTERNET`, the last used by that single request - the departure from `docs/spec/04` is recorded in `IMPLEMENTATION-NOTES.md`. **Airplane-mode execution was not performed.** |
| AC26 | F15 | Install and run the core flow on API 23. | 通过 | Installed, launched, navigated, created a contact, exported an archive, all on `SilverPhone_API23` (Android 6.0, arm64). `minSdkVersion:'23'` confirmed in the APK. |
| AC27 | F11 | 500-contact limit and stale-preview handling. | 通过 | `ContactRepositoryTest.capacityIsEnforcedAtTheLimit` (fills 500, then asserts a 501st is refused as a whole), `aStalePreviewIsRefused` |
| AC28 | F14, F15 | Process death: only a complete old or new set; no call is resumed. | 通过 (模拟器) | Force-stopped the app mid-session and relaunched: all six contacts were present and no dial intent fired. The dial lock and its channel live in memory only, so a new process starts with nothing pending. |
| AC29 | S13 | 关于 is the last entry in 家属设置, and the About page shows the installed version and build number, the GitHub address as text that is also tappable, the check button and the privacy statement - none of which needs a network answer to be read. | 通过 (模拟器) | `17-family-settings-about-en.png` and `05-family-settings-zh.png` (the entry, both languages), `14-about-zh.png` and `16-about-en.png` (the release build), `15-about-update-check-zh.png` (the answer after a real check against the published release); `AboutScreenTest` (3 tests: the version and the address before any check, the download page only when a newer version exists, and an unanswered check never shown as being up to date) |
| AC30 | S13 | The update check names the newest **published release** and never guesses: newer → offers that release's page, same number → 已经是最新版本, nothing published → says so, unreachable or unreadable → says so. A tag pushed without a release is invisible to it by design. | 通过 (规则与模拟器) | `AboutViewModelTest` (7), `GitHubReleaseJsonTest` (5), `AppVersionTest` (8). Driven on the emulator against the published `v1.0.0`: see 18. |
| AC31 | S13, F14 | The app makes no request of its own: the check exists only on this page, only from the tap, and a second tap while one is in flight is not a second request. | 通过 (结构) | `AboutViewModel` starts at `NotChecked` and only `checkForUpdates()` calls the one `ReleaseSource`; `AboutViewModelTest.a second tap while checking does not start a second request`; the merged manifest's only network-using permission is the one named above. **No packet capture was taken.** |
| AC32 | — | The project is called SilverPhone in every place a name appears, in one pass and with no compatibility layer left behind. | 通过 | The renamed tree: root project, `namespace`/`applicationId` `com.silverphone.app`, packages, theme, `silverphone.db` and its schema directory, the archive's format marker and file prefix, the fixture, both READMEs and the release assets. `rg -i` over the tree matches the old name only in `IMPLEMENTATION-NOTES.md`'s account of the rename. |

## 2. Device and visual matrix

| Dimension | Required | Actual |
|---|---|---|
| API 23 (baseline) | yes | Emulator, used for all instrumented tests and manual flows |
| API 28 / 30 / 33 / 35-36 | recommended | **Not run** |
| Widths 320 / 360 / wide | yes | 1080x1920 @ 420 dpi only (= 411 dp wide, two-column home). **320 dp and 360 dp were not measured.** |
| Portrait / landscape | yes | Portrait only. **Landscape was not run.** |
| System font 1.0 / 1.3 / 2.0 × app presets | yes | System font left at 1.0. App preset 大 (two columns) and 超大 (one column) were both exercised and are in `design/screenshots/`. **System font 1.3 and 2.0 were not run.** |
| Real phone with telephony | yes, for real dialling | **Not run.** |

The layout uses measured reflow rather than fixed sizes (column count and row height are
computed from the current window, system font scale and app preset), but this is a design
property, not a measurement, and it is not claimed as verified.

## 3. What is claimed, precisely

**Claimed:** the app builds, installs on Android 6.0, stores and re-displays contacts and
photos, presents one card per relative, produces a valid protocol-v1 archive, reads such
an archive back and rejects the malformed variants, and refuses to dial when the
permission is missing — all with automated tests or device evidence as listed above. The
About screen shows the installed version and the project address, and the update check
names the newest published release of this repository, or says honestly that it could not
read one.

**Not claimed, and not to be inferred from the above:**

- that a real phone call is placed correctly on any device, with any SIM, on one or two
  SIMs, or on any carrier;
- that the app works on Android 7 through 15;
- that it fits a 320 dp screen, landscape, or a 2.0 system font;
- that the system contacts import works against a real address book;
- that a family can move contacts between two real phones;
- that elderly users can use it;
- that the update check was exercised on a real phone or over a carrier network: it was
  driven by hand on the API 23 emulator, whose network is the host machine's;
- that a release built by the workflow installs over an earlier one: both are signed with
  the debug key, so Android refuses the update until the app is uninstalled (see
  `docs/RELEASING.md`);
- that the release APK is production-signed (it is signed with the debug key);
- that performance targets on a 2 GB device are met.

## 4. Evidence index

| Artefact | Path |
|---|---|
| Screenshots | `design/screenshots/` |
| Icon previews (square, circle, grayscale, thumbnail) | `design/icon-previews/` |
| Sample archive with synthetic photos | `design/fixtures/SilverPhone_联系人_测试包.zip` |
| Dependency matrix and build evidence | `build-matrix.md` |
| Room schema v1 | `app/schemas/com.silverphone.app.data.local.AppDatabase/1.json` |
| Lint report | `app/build/reports/lint-results-debug.html` |
| Unit test report | `app/build/reports/tests/testDebugUnitTest/index.html` |
| Instrumented test report | `app/build/outputs/androidTest-results/connected/debug/` |
