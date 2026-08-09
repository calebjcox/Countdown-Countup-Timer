# Countdowns

Home-screen widgets that count down to a moment, or up from one.

A timer targets either **a date** — where the units are years, months, weeks and
days — or **a date and time**, which adds hours, minutes and seconds. You choose
which units appear, so the same target reads however you want it to:

| Units chosen | Widget shows |
| --- | --- |
| years, months, days | `4mo 16d` |
| weeks, days | `19w 5d` |
| days | `138d` |
| days, hours, minutes, seconds | `1d 19:22:18` |
| hours, minutes, seconds | `1:22:18` |

Past targets count up instead, with no separate setting: `3y 220d 13:37:42`.

## Permissions

One, and it is never prompted for:

```
android.permission.RECEIVE_BOOT_COMPLETED
```

It is protection level `normal`, granted at install. Alarms do not survive a
reboot, so without it a widget could sit stale until the system's periodic update
came around.

There is no `SCHEDULE_EXACT_ALARM`, no `INTERNET`, no notification, storage,
location or calendar access. The app makes no network requests of any kind.

Backup does not add to this list. Export and import go through the Storage Access
Framework, where the user picks the file and the app is handed access to that one
file — so there is nothing to grant and nothing to prompt for.

## Backing up timers

Two mechanisms, both permission-free.

**Android's own backup** carries timers to a new phone or through a reinstall,
via `allowBackup` and the `sharedpref` include in
[`data_extraction_rules.xml`](app/src/main/res/xml/data_extraction_rules.xml).
Widget ids are not stable across devices, so `TimerWidgetProvider.onRestored`
hands the old-to-new mapping to `TimerStore.remapWidgets`. Every write also calls
`BackupManager.dataChanged()`, so a change is a candidate for the next backup run
rather than waiting for the system to notice on its own.

That path is invisible and cannot be triggered on demand, so there is also
**export and import**, under the overflow menu. The file is JSON, pretty-printed
and hand-editable:

```json
{
  "format": "countdowns-backup",
  "version": 1,
  "exportedAt": "2026-08-09T14:05:00",
  "timers": [ ... ]
}
```

JSON rather than a copy of `timers.xml`, even though that file is what storage
writes: `TimerStore` keeps the whole list as one `JSONArray` string in a single
preference, so `timers.xml` is escaped JSON inside SharedPreferences' `<map>`
wrapper. Exporting JSON is *less* transformation, not more — and a prefs file
cannot be imported anyway, since dropping one into `shared_prefs/` while the app
is running just loses to the in-memory copy.

Reading is deliberately forgiving, the same way `Timer.fromJson` already was: a
bare array is accepted as well as the envelope, one unreadable record does not
cost the timers either side of it, a repeated id survives once, and a file from a
future version says so instead of failing as corrupt. Import asks whether to merge
or replace; replacing warns first when it would actually drop something.

## How the ticking works

Android will not refresh a widget once per second: `updatePeriodMillis` floors at
30 minutes, and per-second alarms are both battery-hostile and permission-gated.

So a widget showing seconds is not refreshed by this app at all. Its clock portion
is a `Chronometer`, a platform view that ticks inside the launcher's process with
this app not running. The calendar portion — the part that only changes at a day,
month or year boundary — is text, refreshed by a single inexact, non-waking alarm
shared by every widget and aimed at the exact instant the text goes stale.

Three consequences worth knowing:

- **A seconds widget costs essentially nothing.** Between day boundaries it wakes
  this app zero times.
- **A widget with only clock units never schedules anything.** `1:22:18` counting
  down to a meeting is one `Chronometer` and no alarms for the rest of time.
- **The clock reads `H:MM:SS`, not `4h 5m 6s`.** That is `Chronometer`'s own
  format, and it is the price of free ticking. Unit labels apply to the calendar
  part. For the same reason, choosing seconds quietly enables minutes and hours —
  a minutes-and-seconds-only widget would still render as `H:MM:SS`, so the units
  shown and the units chosen are kept honest.

Alarms are inexact and non-waking on purpose. A widget only matters when someone
is looking at it, and the device is awake then. In deep doze an update can be
deferred; the half-hourly `updatePeriodMillis` is a system-managed backstop that
survives reboots, and unlocking the phone flushes pending alarms.

## Reading on a wallpaper

By default a widget has no background of its own — the text sits straight on the
wallpaper, the way the platform clock widget does. That raises the same question a
launcher has about its icon labels: light text or dark?

The answer is a property of the **wallpaper**, not of the system theme, and the two
disagree exactly when it matters — a phone in light mode with a dark photo behind
the widget wants light text. So the widget asks the wallpaper, through the same
`WallpaperColors.HINT_SUPPORTS_DARK_TEXT` signal the launcher uses. This needs no
permission. Every line also carries a shadow, because one photo can be bright at
one end of a line of text and dark at the other.

If a wallpaper publishes no hint — live wallpapers are entitled not to — the widget
falls back to the system theme. **Text color** in the editor overrides the whole
thing with an explicit Light or Dark per timer. Switch the background on instead
and the question disappears: text on our own panel is a matched pair with it, so
the setting hides itself.

One caveat: `ACTION_WALLPAPER_CHANGED` is not delivered to manifest receivers, so
after changing wallpaper the colour settles on the widget's next refresh rather
than instantly — at worst the half-hourly `updatePeriodMillis` backstop.

## Dates behave like dates

Targets are stored as wall-clock times, not instants. A birthday on December 25th
is still December 25th after the phone crosses a time zone, rather than sliding by
the offset difference. Everything is computed with `java.time` on zoned values, so
a day across a daylight-saving change is still one day (`2d`, even though the real
elapsed time is 47 hours), months keep their real lengths, and February 29th is
handled by the platform rather than by arithmetic on millisecond counts.

## Privacy

The app collects no data and makes no network requests. Timers leave the device
only through the two backup routes above, both of which the user drives: the
phone's own Google backup, and a file exported to a location they pick. See
[PRIVACY.md](PRIVACY.md) for the full policy, or
[the hosted version](https://calebjcox.github.io/Countdown-Countup-Timer/) on
GitHub Pages — this is the URL used in the Play Store listing.

## Project layout

```
core/   pure Kotlin, no Android imports — all the date math, unit tested on a JVM
app/    the Android application: widget provider, renderer, alarm scheduler, screens
```

The split is deliberate. Everything that decides *what a widget says* lives in
`core` and is covered by tests that need no emulator: leap days, month clamping,
daylight saving in both directions, every unit subset, count-up symmetry, and that
the scheduled refresh lands exactly on the boundary where the text changes.

```
./gradlew :core:test              # the date math
./gradlew :app:testDebugUnitTest  # the backup format
./gradlew :app:assembleDebug
```

`minSdk` is 31 (Android 12), which is where responsive widget layouts,
reconfigurable widgets, the Material You system palette and the widget
corner-radius dimensions all landed — so there is no version branching in the code
at all. Built against Android 17 (API 37), which is what a Pixel 10 runs.

Runtime dependencies are `kotlin-stdlib`, `core-ktx`, `appcompat`, Material
Components and RecyclerView. No Glance, no Compose, no WorkManager, no Room, no
DataStore. Storage is `SharedPreferences` plus the platform's own `org.json`.

`org.json:json` appears as a test-only dependency of `:app`, because the `org.json`
in `android.jar` is a stub whose every method throws under unit tests. On a device
it is the platform's implementation that runs, as it always was.

## Installing without Android Studio

Every push builds a debug APK in GitHub Actions. Open the run, download the
`countdowns-debug-apk` artifact, unzip it, and install `app-debug.apk` on the
phone.

Debug builds install as **Countdowns (debug)**, under their own application id
(`com.calebjcox.countdownwidgets.debug`). That makes them a separate app from a
release install, with separate timers and separate widgets — the two sit next to
each other and neither can overwrite the other. Each successive debug APK does
update over the last one, because they are all signed with the same key.

### The debug keystore is committed, deliberately

`app/debug.keystore` is in the repository on purpose, and it is **not a leaked
secret**. It is the standard Android debug keystore, holding the same public
credentials every debug key uses — alias `androiddebugkey`, password `android`,
both of which are published in Google's own documentation. Anyone can generate an
identical one in a second.

It is committed because the alternative is worse: with no keystore checked in, the
build tools invent a fresh random one on each machine, so every CI run would sign
its APK with a different key and Android would refuse to install any of them over
the last. Committing it is what makes the download-and-install path above work
more than once.

Its blast radius is one package. Because debug builds carry the `.debug` suffix,
this key can only ever sign `com.calebjcox.countdownwidgets.debug` — it cannot
sign, update, or impersonate the released app. The key that signs releases is not
in this repository and never will be.

## Adding a widget

Long-press an empty spot on the home screen → **Widgets** → **Countdowns**, then
drag it out. You will be asked which timer it should show. Tapping a widget opens
that timer for editing; long-pressing offers **reconfigure** to point it at a
different one.

Timers and widgets are separate: one timer can drive several widgets, deleting a
widget leaves the timer alone, and deleting a timer leaves its widgets asking for a
new one rather than vanishing.
