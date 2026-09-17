# Implementation notes

Decisions a reviewer would reasonably question, and where the code departs from the
letter of the design documents. Each one says what was decided and why.

## Deliberate departures requested by the project owner

The owner reviewed a build on a real phone on 2026-09-17 and directed the following.
These override the corresponding lines in the source documents, which are otherwise
still in force.

| Source document said | Now | Why |
|---|---|---|
| S02 家属入口确认: a second confirmation screen between the home screen and settings ("点“家属设置”，再点“进入设置”") | **Removed.** 家属设置 opens the settings screen directly. | The owner judged the intermediate page pointless: it added a step without protecting anything, since it is a mis-tap guard and not authentication. |
| 字号档位 default 大 (1.2) | **Default is now 标准 (1.0)** | The owner's view is that the base sizes are already comfortable, and a family can raise them if needed. The four presets are unchanged. |
| One `:app` module, package-level layering | Same module, but the two largest screen files were split | See "Modularity" below. |

A second review on the same day added the following, all of which also override the
documents.

| Source document said | Now | Why |
|---|---|---|
| 7.2 字号基准: page title 32 sp, contact name 26 sp, buttons 24 sp, body 22 sp, caption 18 sp at ratio 1.0 | **20 / 16 / 14 / 14 / 12 sp** | Two rounds of the owner's review. The first round had set 28 / 22 / 16 / 16 / 14, and the owner then reported that the default still read as enlarged: a page whose every line is bigger than the rest of the phone is a crowded page. The standard preset is now the phone's own scale (Material's titleLarge, titleMedium, labelLarge, bodyMedium, bodySmall), and the three presets above it - 1.2 / 1.4 / 1.6 - are what the family reaches for. |
| S01: a tap anywhere on a relative's card places the call | **Only the green button dials.** The photo and the name are inert. | Found on a real phone: the photo is the largest thing on a card, so a resting hand or a scroll that ended on a card could dial. The card is still one announcement; the photo is still how a person is recognised. |
| S03: separate 从文件导入 and 导出亲人 entries | **One entry, 导入 / 导出**, with both actions on the screen behind it | Reading a file and writing one are two ends of the same job - one phone hands a ZIP to another - and two menu rows made the family choose a direction before knowing which one they needed. |
| 拨号权限与使用说明 as the place to grant the call permission | The permission is **requested inline, at the moment of the press**; the help screen remains for the explanation and for the case where the system refuses | Owner's complaint: pressing Call and being sent to a help page to read where to tap, then tapping again, was the worst flow in the app. Granting it now places the call the user already asked for. |
| (no icon specified) | Launcher icon built from the artwork the owner supplied | `tools/GenerateIcons.java` derives the legacy mipmaps, the adaptive foreground, the monochrome layer and the gradient background from `design/icon-source.png`. |

Everything else in the documents — the dialing rules, the archive protocol, the
placeholder colours, the touch-target minimums, the call boundary — is unchanged.

## The visual system (2026-09-18 revision)

The owner reviewed the first build and called the interface ugly, dated and cramped,
and asked for press feedback that can actually be seen. What follows is the system
that replaced the first draft. The numbers are Material 3's published tokens rather
than invented ones, so a reviewer can check them against the design system itself.

| Layer | Value | Source |
|---|---|---|
| Card and list corner | 20 dp (`ShapeTokens.CornerLargeIncreased`) | Material 3 shape scale; the first draft used 16 dp |
| Action buttons | Stadium (`RoundedCornerShape(percent = 50)`) | Material 3's default button shape |
| Small row controls | 14 dp chip corner | Between the small (8 dp) and medium (12 dp) steps |
| Elevation | 2 dp resting, 0 dp while held | Material 3 elevation level 1-2 |
| Type | 20 / 16 / 14 / 14 / 12 sp | Material 3 titleLarge / titleMedium / labelLarge / bodyMedium / bodySmall |
| Dial colour | `#15803D`, white label = 5.0:1 | The first draft's `#146C43` read as office-teal; the contrast floor the specification sets is 4.5:1 |
| Brand accent | `#D6FF00` bar under each page title, and the launcher icon | The specification keeps the brand colour to small areas; inside the app that is one 44 x 4 dp rule per screen |

### Press feedback

A tap has to be acknowledged three times over, because the people using this app are
the least likely to notice a subtle one. Every control that can be pressed now:

- darkens to an explicit pressed fill (the Material button only tints by a few
  percent, which is invisible on the dark fills this app uses);
- shrinks to 96% under the finger and springs back on release with a small overshoot;
- carries a ripple in the label's colour;
- gives one short platform tick on the way down (`HapticFeedbackConstants.VIRTUAL_KEY`,
  which the system silences when the user has haptics turned off).

The home screen's green dial area is the control this matters most for, so it also
sinks flat onto the card while it is held.

### The home card

The first draft's card was a white frame around a photo with a full-width green band
under it, which read as three stacked stripes. The card is now built the way a media
card is built: the face bleeds to the card's edges with nothing around it, the name
sits on a shallow band under it, and the green dial block closes the card with the
card's own bottom corners. The placeholder face was also softened - a 55% ink
silhouette at 42% of the tile instead of a solid navy poster - because that is what a
family sees before they have added a photo.

## Adaptive layout (2026-09-18)

The interface was rebuilt on Google's canonical adaptive layout system rather than on
hand-rolled `Row`/`Column` branches. What that means here, and where the product rules
decided the answer:

| Question | Answer | Why |
|---|---|---|
| Which window size class | `androidx.compose.material3.adaptive.currentWindowAdaptiveInfo()`, read through `ui/adaptive/WindowLayout.kt` | Nothing in the app branches on a device model, a screen diagonal or a raw pixel count. A phone in split screen and a small foldable land in the same class and get the same layout. |
| How many navigation destinations | Three: `home`, `family`, `editor` | The family area is one destination because the menu and the screen behind it are the two panes of one scaffold. Every previous settings route is gone. |
| The family area | `ListDetailPaneScaffold` + `rememberListDetailPaneScaffoldNavigator` | Compact: the menu and the section are one at a time, with a pane transition. Medium and wider: the menu stays beside the section it opened, and the section drops its back arrow because there is nothing to go back to. The scaffold owns that back stack. |
| The home screen | One pane, `LazyVerticalGrid` | There is exactly one thing an elderly user does, so there is nothing to navigate between and no navigation suite: a bottom bar with one tab would be decoration. |
| How many columns of relatives | Measured: the grid uses two columns when the current width fits four characters at the current text size | This is the one structural decision a size class cannot make, because it depends on the text *inside* the window and on the system font scale, both of which change inside one size class. It is the only `BoxWithConstraints` in the app, and it exists for that reason. |
| Reading width | Content is capped at 600 dp and centred | A row of settings text stretched across a tablet is unreadable, which is the case the 600 dp measure exists for. |
| Insets | Collected once by the `Scaffold` each screen uses and applied once to its content column | No screen reads `WindowInsets` by hand any more, so nothing can double-pad or swallow the navigation bar. |
| State | Hoisted: screens take state and callbacks, panes take the numbers they draw | `FamilyMenuPane(contactCount = ...)` is a pure function of its arguments, which is also what makes it previewable without a ViewModel. |
| Lazy lists | Stable `key` and `contentType` on every item, `Modifier.animateItem()` where order can change | Reordering a relative slides the card instead of redrawing the list. |
| Previews | `ui/preview/ScreenPreviews.kt`: home and family menu at phone and tablet sizes | The two layouts that have to survive both windows are the ones kept in front of a reviewer. |

Material 3 Adaptive is pinned at **1.2.0**: 1.3.0 requires `compileSdk 37` and AGP 9.1,
the same wall the Compose 1.12.x line hits, and 1.2.0 declares `minCompileSdk 35`,
`minSdk 21` and AGP 8.6 - all inside the versions this project pins.

### Motion

Every animation in the app is short and answers something the hand just did:

- pressing any control darkens it, shrinks it and springs it back (see above);
- moving between destinations slides and fades in 220 ms, so the direction of travel is
  visible;
- switching a pane in the family area is the scaffold's own transition;
- the home screen crossfades between loading, empty, failed and loaded instead of
  replacing the page in one frame, and the "handing off to the phone" notice fades and
  scales in;
- list rows and grid cards animate their placement.

No animation runs longer than 220 ms, none of them loops, and none of them is the only
way to know that something happened.

### One design, six rules

The owner asked for an audit of the whole interface: sizes, look, corners, colours,
interaction and weight, so that the app does not read as several apps stitched together.
The audit produced these rules, and the code now obeys them everywhere:

| Rule | What is allowed | How it is enforced |
|---|---|---|
| Text size | The six roles in `AppTypography` (20 / 16 / 14 / 14 / 12 sp) | Nothing under `ui/` sets a size: `grep -rE "[0-9]+\.sp" ui/` finds nothing outside the theme |
| Weight | Medium for the four emphasis roles, Normal for body and caption | One `fontWeight` per role in `AppTypography`; the first build used SemiBold and read as shouting |
| Corners | Card 20 dp, chip 14 dp, badge 8 dp, controls stadium | `AppDimens` carries all four; no literal `RoundedCornerShape(<number>)` is left under `ui/` |
| Colour | The `AppColors` palette only | No `Color(0x…)`, `Color.White` or `Color.Black` under `ui/` outside the theme |
| Spacing | 4 / 8 / 12 / 16 / 24 dp between elements | Every `padding`/`spacedBy` reads an `AppDimens` field; component sizes (a 64 dp thumbnail) are the only literals left |
| Interaction | Tap opens, press and hold selects, destructive actions confirm, save lives in the bar | The management list and the import picker now answer a tap the same way, and the editor, the text-size page and the language page all keep Save in the same corner |

Two deliberate exceptions, both about the person using the phone rather than the person
setting it up: the home screen's dial area keeps its 64 dp height and its 40 dp handset,
and the whole-screen help and empty states use the same oversized button. Everything the
family touches is ordinary app sized.

### Two rules the layout is checked against

Both came out of the owner reading a screenshot of the two-pane layout and pointing at
things that were structurally wrong rather than merely plain.

1. **Both panes of a two-pane screen start their content on the same line.** The list
   pane had a subtitle under its title ("5 contacts at the moment") and the detail pane
   did not, so the two columns began a whole line apart and the page looked broken.
   Every family screen now has the same header - a one-line bar plus one muted
   description - and the description is the same sentence the menu row uses.
   Verified by reading the accessibility tree: both subtitles at y=171, both titles at
   y=93, in a 1440 x 1067 dp window.
2. **A label sits flush with the container it labels.** Section headings had a 4 dp
   inset against their card, which reads as a mistake rather than as hierarchy.

### The fluorescent colour

`#D6FF00` was in three places: the launcher icon's background, a tile behind the family
entry on the home screen, and the selected chip on the language and dialling-code
screen. The owner asked for it to be taken out of the interface, so the two in-app uses
are gone - the family entry now uses the same sunken tile as every other row, and the
selected chip uses the same quiet ink tint as every other selected control. The colour
scheme's `primaryContainer` also pointed at it, which would have painted any Material
component that reached for that role; it now points at the neutral tint, so
`AppColors.BrandLime` is read by nothing under `ui/`. What remains is the launcher
icon, which is the owner's own artwork.

## Modularity

The project stays a single Gradle module: the technical document asks for that
explicitly, and splitting it would be a large change with no functional gain for an
app this size. What did change is file size, because two screens had grown past 600
lines, which is the point at which a file stops being readable in one pass:

| File | Before | After |
|---|---|---|
| `ui/contacts/ContactsImportScreen.kt` | 679 lines | 361, with the permission states, pick list and dialogs moved to `ContactsImportPieces.kt` (366) |
| `ui/transfer/FileImportScreen.kt` | 616 lines | 371, with the six step bodies and the replace dialog moved to `FileImportPieces.kt` (306) |

The rule applied was that a screen file owns *arrangement* — which state shows what —
and a pieces file owns the *rendering* of one state. No file in `ui/` is now over
400 lines.

The layering itself was already in place and is unchanged: `domain/` holds pure
rules with no Android types, `platform/` holds every Android system boundary,
`data/` owns storage and the single write path, and `ui/` holds screens and their
ViewModels. Features are separate packages (`home`, `family`, `editor`, `contacts`,
`transfer`, `settings`), and none of them reaches into another's internals.

## Where the implementation differs from the documents

### S06 (photo crop) is a step inside the editor, not a separate navigation destination

The UI document lists S06 as its own page. In the code, choosing a photo sets a pending
URI in the editor destination and the crop screen takes over the whole editor surface
until the family confirms or cancels (`ContactEditorScreen` → `PhotoCropScreen`).

The reason is the navigation rule in the technical document: only identifiers may travel
through the back stack, and no bitmap may be put in a navigation argument or saved state.
Making the crop a separate destination would mean either passing an image reference through
navigation or keeping the draft photo in two places. As a step inside one destination, the
draft lives in exactly one ViewModel, and cancelling cannot leave a half-cropped photo in a
back-stack entry.

What the user sees is unchanged: a full-screen step with its own buttons and copy, and
system back cancels the crop rather than discarding the editor behind it.

### Photo normalisation always centre-crops on the import path

For an imported archive, the photo is already square, so the centre crop is a no-op. For a
selected file, the crop screen decides the framing and the normaliser then only enforces
the size limits. Both paths converge on the same encoder ladder.

### The placeholder colour of imported contacts is the default

The logic document says the family chooses the colour, and that it must not be random. An
import therefore assigns `LIGHT_BLUE` to every contact that arrives without a photo, rather
than cycling colours, so nothing about the result depends on the order of the archive. The
family can change each one afterwards.

### `photo: null` is required, not optional

A missing `photo` field is a protocol error rather than an implied "no photo". Strict
decoding is configured with `explicitNulls = true` so the writer always emits the field.

## Decisions that were not obvious

### The dial lock is application-scoped, and the request has two guards

`DialCoordinator` lives in `AppContainer`, not in a screen ViewModel, so its lock survives
recomposition, rotation and Activity recreation. The request is published on a `Channel`
and only `MainActivity` collects it, while started. Two independent guards stop a second
call:

1. `claimForDispatch(eventId)` — a request that was already launched can never be launched
   again, even if the host re-collects it after a recreation.
2. The card is disabled while `DialState.blocksFurtherTaps`, which is the UI-level
   expression of the same rule.

Process death discards both, which is the required behaviour: a new process has nothing
pending.

### "Minimum spacing" is a subtraction, so the sentinel cannot be `Long.MIN_VALUE`

The first version used `Long.MIN_VALUE` as "never dialled", and `now - Long.MIN_VALUE`
overflows to a negative number, so the very first tap was always refused. The field is now
nullable. This was caught by a unit test, not by reading the code.

### The management row puts its actions on a second line

Placing 上移 / 下移 / 修改 beside the text squeezed an 11-digit number onto two lines. The
photo, name and full number now take the whole width and the three labelled actions sit
underneath. This also satisfies the requirement that reordering be done with labelled
buttons rather than drag gestures or unlabelled icons.

### Two columns are decided by measurement, not by screen width

The home screen measures four characters in the current name style — which already carries
both the system font scale and the app preset — and only uses two columns when the window
is at least 360 dp **and** two columns of that measured width plus the card padding and the
gap actually fit. Row heights are levelled by measuring each name's line count and having
every card in a row reserve the row's maximum, so a long name raises its row instead of
being truncated or shrinking its neighbours.

### The exchange archive is validated by counting, not by trusting

Every size limit is counted from the bytes read: the archive copy, each entry, and the
running total. The ZIP header's declared uncompressed size is never used for a decision.
Entry names are only matched against the manifest; files on disk are always named by the
reader, so no archive name can influence a path.

### `ContactPhoto` does not touch the image loader for a placeholder

The loader is read lazily, after the "no photo" branch. A contact list made only of
placeholders therefore does not depend on an image loader being provided — which also made
the Compose tests able to render real screens without wiring Coil.

### The revision counter creates its settings row if it is missing

`bumpRevision()` inserts the settings row when absent before incrementing. Without it, a
missing row would make `UPDATE ... WHERE id = 1` affect nothing, the revision would stop
advancing, and every later import preview would look stale forever.

### Test fixtures are generated, never photographed

`tools/GenerateSampleArchive.java` draws every image from shapes. There is no photograph of
any person anywhere in the repository, and the phone numbers in the fixture are
placeholders that must not be dialled.

## The dial is started by the Activity, not by the application context

This was wrong in the first build and made the app's core feature useless on a real
phone, so it is worth stating as a rule rather than a bug report.

`AndroidPhoneLauncher` originally held the application context and called
`startActivity` on it. Android refuses that without `FLAG_ACTIVITY_NEW_TASK` and throws
`AndroidRuntimeException`, which the launcher's own error handling caught and turned into
`LaunchOutcome.Failed`. The user therefore saw "电话没有打开，请再试一次" *with the
permission already granted*, and no call was ever placed. The emulator tests had only
covered the missing-permission path, so nothing caught it.

The fix separates the two responsibilities:

* `CallPermissionGate` answers "may we call", takes no Android Context, and is what the
  dial lock depends on — so the lock stays plain JVM code.
* `PhoneLauncher.launch(host, number)` requires the *foreground Activity*, and
  `MainActivity`'s host loop passes `LocalContext.current`. The flag is added only if the
  caller turns out not to be an Activity.

The general lesson is recorded here because it will recur: a caught exception that is
mapped to a user-facing "something went wrong" message can hide a bug that a crash would
have surfaced immediately, and any code path that starts an Activity needs its context
chosen deliberately.

## Back navigation

Every screen is a pushed destination, so the system back returns to its parent and the
only place back leaves the app is the home screen, which is what a user expects there.
The one exception was the dial-help screen, which is an overlay inside the home
destination rather than a destination of its own: back there popped the only entry in the
graph and dropped the user onto the phone's home screen, which looks like a crash. A
`BackHandler` now closes the help instead.

Each screen also offers an explicit button back to its parent (`返回` / `取消`).

## The stored language has to be applied, not just stored

The language setting was written to the database and applied when Save was pressed, and
never again. A cold start therefore came up in the system language: the family chose
Chinese, the row said `zh`, and the app was in English. Two changes fixed it.

`App.applyStoredLanguage` reads the stored tag and applies it **before the first Activity
attaches its base context**. The read blocks, which is deliberate: it is one row on a
local database, and applying the locale later means the Activity is recreated and the
interface visibly flips language after it has already been drawn. If the read fails the
app follows the system, which is the same as having no choice stored.

`PreferencesViewModel.save` applies the locale on **every** save, not only when the tag
changed. The value in the database and the value in force can legitimately disagree - a
cold start is exactly that case - and pressing Save is the family saying "make it so".
Applying a tag that is already in force is a no-op.

Both facts now live in one place, `app/AppLanguage`, so the settings screen no longer
knows about `LocaleListCompat` and the Application no longer knows what an empty tag
means.

## The help overlay waits for the permission dialog

Requesting the permission inline meant the help screen was already drawn behind the
system dialog, which reads as though the app had given up on the tap that asked for it.
`HomeUiState.awaitingPermission` is true while a tap is waiting on the dialog - that is
`pendingContactId != null` and the coordinator reporting a missing permission - and the
help overlay is held back until the answer arrives. Denying still shows it, which is the
whole point of that screen.

## The text-size preview is pinned, and the home header has one shape

The text-size page scrolled as one block, so the fixed button row sliced the preview card
in half at the standard preset - a card cut by a bar reads as a broken layout, unlike a
half-visible option row, which is the normal "scroll for more" affordance. The options
now scroll and the preview is pinned above the buttons, so the thing the family is
choosing *for* is always whole.

The home header used to switch between a row and a stacked column depending on a measured
width, and the stacked form left a lone pill under a large title with dead space beside
it. It is now always one row: the title takes what the entry does not need and wraps if
it has to, and the entry is always on the trailing edge, whatever the width, the preset
or the system font scale.

## Focus dismissal on API 23 is a platform behaviour, not an app bug

Tapping outside the search field on the management screen calls
`focusManager.clearFocus()`. On the API 23 emulator the field is cleared and then
immediately re-focused: hiding the IME makes the window regain focus, and Compose hands
focus back to the field it was taken from. This was measured with `onFocusChanged`
(`[false, true, false, true]`), not guessed. On current Android the field simply loses
focus. The regression test therefore asserts the part the screen owns - the tap is not
swallowed and the controls under it still work - and not the focus transition.

## The dialling code drops a national trunk zero

`CountryCode.apply` removes one leading `0` before adding the code, and `+39` (Italy) is
the exception, because Italian national numbers keep their zero abroad.

The zero is the domestic trunk prefix, and it is exactly what a country code replaces.
Without this the app dialled `+8601012345678` for a Beijing landline - a number the network
rejects - and the failure was silent: the family saw the call not connect and had no way to
tell that the app had composed the number wrongly. The prefix is still applied at dial time
and still never rewrites a stored contact, so the editor's preview is the place to see what
will actually be dialled, and it always shows the composed number.

## What the exchange file is not

Two limits are accepted deliberately rather than solved:

- **The import preview holds every photo in memory.** A limit-legal archive (500 contacts,
  128 KiB per photo) is about 60 MiB of live byte arrays, which a low-memory device would
  not survive. The photos are verified on disk before they are decoded, so the fix exists -
  keep the staging directory until the commit and read from it - but it is a rewrite of the
  import path, and real families hold a handful of relatives. A failure here is an
  out-of-memory kill during a preview, not data loss: nothing is committed until the plan
  is applied in one transaction.
- **A process killed mid-export leaves a partial ZIP** in `cache/exports/` until a launch
  more than 24 hours later removes it. It is never offered for sharing, because the app that
  was writing it is gone.

## Reaching a call from a tap that had to ask for permission

The tap that raises the system permission dialog is accepted before the dialog appears, and
its timestamp starts the 1 s spacing window. The second half of that tap - the call the user
already asked for - is therefore issued with `resumed = true`, which skips the spacing
check. Without it, answering the dialog quickly threw the call away with no message at all,
which is worse than the extra tap the inline request was introduced to remove.

## Failures that used to end the process

Three places reported failure by throwing into a coroutine that had no handler, so any
unlucky write or read ended the app:

- The tap handler in `DialCoordinator` now catches everything, reports a
  `StorageUnavailable` problem, and leaves the latch open. The state it used to reach -
  `Checking` forever - disabled every card with no way back.
- The three preference writers return a Boolean; both settings screens stay open and say so
  when a save fails.
- The contacts read reports `NotPermitted` or `Failed` instead of pretending the address
  book is empty.

`AppContainer` also installs a `CoroutineExceptionHandler` on the application scope. It
should never fire; it exists because a crash on a phone whose owner cannot be told why is
the worst outcome this app can produce.

## The name is SilverPhone everywhere, including inside the files the app writes

The project was built as "FamilyDialer" and is now SilverPhone in every place a name is
written down: the Kotlin package and the applicationId (`com.silverphone.app`),
`rootProject.name`, the launcher label in both locales, the Compose theme
(`SilverPhoneTheme`), the database file (`silverphone.db`), the export file name
(`SilverPhone_contacts_` / `SilverPhone_联系人_`) and the format marker inside the archive
(`silverphone-backup`, see `BackupManifest.FORMAT_MARKER`).

The last two are the only names a *previous* build could have written into a file, and they
were renamed rather than kept as compatibility aliases. Nothing has been released, so no
archive exists outside this machine that a new build has to keep reading, and a product
that answers to two names inside its own file format is a permanent source of confusion for
whoever reads this repository next. The consequence, stated plainly: an archive written by
an earlier local test build is now rejected as an unsupported file instead of being
half-imported.

## The About screen, and the one network call the app is allowed to make

`docs/spec/04_软件技术栈_AI提示词.md` answers "INTERNET 否" in its permission table, and the
manifest used to carry a comment saying the permission was deliberately absent. The project
owner asked for a version, a tappable GitHub address, a working update check and a privacy
statement on an About screen, and a check that never contacts anything cannot exist. The
app therefore declares `INTERNET` - the only departure from that table.

What stops it from becoming a general-purpose network permission:

- exactly one request exists (`GET /repos/Changjingjiu/SilverPhone/releases/latest`), and it
  is started by exactly one tap. There is no check on start-up, no interval, no refresh and
  no retry in the background;
- it is anonymous: a `User-Agent` naming the app and nothing else. No identifier of the
  phone, the family or the contacts is sent, and no body is sent at all;
- nothing is downloaded or installed. A newer release shows its version number and opens
  the release page in the browser; installing stays a deliberate act by a person;
- offline is a state, not an error path: `Unreachable` is reported as "could not read the
  newest version", never as "you are up to date", and every other screen works with no
  connection at all;
- a repository with no published release answers 404, which is its own state ("nothing has
  been published yet") rather than a failure, because that is what a brand-new project
  looks like.

Two details of the version comparison are worth recording, because both were choices:
components are compared as numbers (`1.10.0` is newer than `1.9.0`, which text comparison
gets backwards), and a build whose own name carries a pre-release suffix - the debug build
ships as `1.0.0-debug` - counts as *older* than the release with the same number. That is the
semantic rule, and it means a debug build is offered the release rather than being told it is
already current. A tag that cannot be read as a version is reported as an unreadable answer,
never as an update.

## Known implementation gaps

- The sample archive and the app's own export both contain placeholder numbers. Nothing in
  the automated tests places a real call; every test uses a recording host double.
- The system contacts import path (S07) was exercised on an emulator with a seeded address
  book, including the multi-number and over-long-name cases, but not against a real phone's
  address book.
- Landscape, narrow (320 dp) windows and the largest system font combined with the largest
  app preset have not been measured.
- Modularity is one Gradle module with feature packages. Splitting into Gradle modules was
  not done; the technical document asks for a single module, and the change would not alter
  behaviour.
