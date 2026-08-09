# Privacy Policy for Countdowns

**Last updated:** August 9, 2026

Countdowns ("the app") is a home-screen widget app that counts down to, or up
from, dates and times you choose. This policy explains what happens to your
data when you use it.

## Summary

The app does not collect, transmit, or share any data. Everything you enter
stays on your device.

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

## Data storage

The only information the app handles is what you enter to configure your
timers: names, target dates and times, and display preferences (which units
to show, colors, and similar settings). This is stored locally on your
device, in the app's private storage, using Android's standard
`SharedPreferences` mechanism. It is never transmitted off the device, and no
one but you — not the developer, not any third party — has access to it.

Deleting a timer within the app removes its data immediately. Uninstalling
the app removes all of its data from your device.

## Permissions

The app requests one Android permission:

- **`RECEIVE_BOOT_COMPLETED`** — a normal-protection-level permission granted
  automatically at install, with no prompt. It lets the app reschedule its
  widget-refresh alarm after your device restarts, so a widget doesn't sit
  showing stale text until the system's next periodic update. It is not used
  for any other purpose.

The app does not request access to your location, contacts, calendar,
storage, camera, microphone, or any other sensitive permission.

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
