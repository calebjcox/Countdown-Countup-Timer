# Privacy Policy for Countdowns

**Last updated:** August 9, 2026 (backup section added)

Countdowns ("the app") is a home-screen widget app that counts down to, or up
from, dates and times you choose. This policy explains what happens to your
data when you use it.

## Summary

The app does not collect, transmit, or share any data. Everything you enter
stays on your device, unless you use one of the backup options described below —
your phone's own Google backup, or a file you export yourself. Both are under
your control, and neither sends anything to the developer.

## Data collection

The app collects no personal information, usage data, analytics, or
diagnostics of any kind. It contains no advertising, analytics, or
crash-reporting software development kits (SDKs), and it does not use
cookies, device identifiers, or any similar tracking technology.

## Network access

The app does not request the `INTERNET` permission and cannot make network
requests. It has no server, no account system, and no connection to any
third-party service. This is a property of the app's build, not just its
current behavior — it is architecturally incapable of sending data anywhere.

The backup options described below do not change this. Android's backup runs
in the operating system, not in the app, and an exported file is handed to
whichever app you choose in the file picker. In neither case does Countdowns
itself open a network connection — it cannot.

## Data storage

The only information the app handles is what you enter to configure your
timers: names, target dates and times, and display preferences (which units
to show, colors, and similar settings). This is stored locally on your
device, in the app's private storage, using Android's standard
`SharedPreferences` mechanism. No one but you — not the developer, not any
third party — has access to it. The app itself never transmits it anywhere;
the only ways a copy leaves your device are the two backup mechanisms below,
both of which you control.

Deleting a timer within the app removes its data immediately. Uninstalling
the app removes all of its data from your device.

## Backup

**Your phone's Google backup.** The app marks its timer data as eligible for
Android's standard backup, the same mechanism that carries your other apps'
data to a new phone. If you have backup turned on in your phone's settings,
a copy of your timers is stored in **your own Google account**, under
[Google's terms and privacy policy](https://policies.google.com/privacy), and
is restored automatically when you reinstall the app or set up a new device.
Android handles this end to end: the developer does not operate it, is not
told when it happens, and has no access to what it stores. Turning backup off
in your phone's settings stops it, and no timers are backed up this way.

**Files you export.** The app can write your timers to a JSON file. You choose
where that file goes using Android's own file picker — local storage, an SD
card, a cloud drive, wherever you point it — so the app never needs a storage
permission and never picks a destination for you. Once a file is written it is
yours and outside the app's control: if you save it to a cloud service, that
service's own privacy policy applies to it. Nothing is exported unless you tap
Export, and no copy of an exported file is kept or sent anywhere else.

## Permissions

The app requests one Android permission:

- **`RECEIVE_BOOT_COMPLETED`** — a normal-protection-level permission granted
  automatically at install, with no prompt. It lets the app reschedule its
  widget-refresh alarm after your device restarts, so a widget doesn't sit
  showing stale text until the system's next periodic update. It is not used
  for any other purpose.

The app does not request access to your location, contacts, calendar,
storage, camera, microphone, or any other sensitive permission.

Exporting and importing timers does not change this. Those use Android's
Storage Access Framework, where you pick the file yourself and the app is
handed access to that one file — so no storage permission is needed, and you
are never asked to approve one.

## Children's privacy

Because the app collects no data from any user, it does not knowingly
collect data from children, and none of the exceptions under the
Children's Online Privacy Protection Act (COPPA) or similar laws apply.

## Changes to this policy

If this policy changes, the "Last updated" date above will change
accordingly, and the new policy will be posted at this same location.

## Contact

Questions about this policy can be raised by opening an issue on the
[project's GitHub repository](https://github.com/calebjcox/Countdown-Countup-Timer/issues).
