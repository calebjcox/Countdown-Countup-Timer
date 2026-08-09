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

## Dates behave like dates

Targets are stored as wall-clock times, not instants. A birthday on December 25th
is still December 25th after the phone crosses a time zone, rather than sliding by
the offset difference. Everything is computed with `java.time` on zoned values, so
a day across a daylight-saving change is still one day (`2d`, even though the real
elapsed time is 47 hours), months keep their real lengths, and February 29th is
handled by the platform rather than by arithmetic on millisecond counts.

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
./gradlew :core:test        # the date math
./gradlew :app:assembleDebug
```

`minSdk` is 31 (Android 12), which is where responsive widget layouts,
reconfigurable widgets, the Material You system palette and the widget
corner-radius dimensions all landed — so there is no version branching in the code
at all. Built against Android 17 (API 37), which is what a Pixel 10 runs.

Runtime dependencies are `kotlin-stdlib`, `core-ktx`, `appcompat`, Material
Components and RecyclerView. No Glance, no Compose, no WorkManager, no Room, no
DataStore. Storage is `SharedPreferences` plus the platform's own `org.json`.

## Installing without Android Studio

Every push builds a debug APK in GitHub Actions. Open the run, download the
`countdowns-debug-apk` artifact, unzip it, and install `app-debug.apk` on the
phone.

## Adding a widget

Long-press an empty spot on the home screen → **Widgets** → **Countdowns**, then
drag it out. You will be asked which timer it should show. Tapping a widget opens
that timer for editing; long-pressing offers **reconfigure** to point it at a
different one.

Timers and widgets are separate: one timer can drive several widgets, deleting a
widget leaves the timer alone, and deleting a timer leaves its widgets asking for a
new one rather than vanishing.
