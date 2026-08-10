# Play Store listing assets

Everything Google Play asks for, generated from the app's own code. Regenerate with:

```bash
./gradlew :app:testPlayAssetsUnitTest -PplayAssets
```

That rewrites every file in this directory. It needs an Android SDK with
`platforms;android-37.0` and `build-tools;37.0.0`, the same as any other build here.

## What goes where in Play Console

| Play Console field | File |
| --- | --- |
| App icon | `icon-512.png` |
| Feature graphic | `feature-graphic-1024x500.png` |
| Phone screenshots | `phone/01…08` |
| 7-inch tablet screenshots | `seven-inch/01…08` |
| 10-inch tablet screenshots | `ten-inch/01…08` |

Play accepts at most **eight** screenshots per slot, so the eight numbered files in
each directory are the whole upload, in order. `extras/` holds further shots — the
editor's Appearance section with its live preview, and the timer picker the launcher
shows when a widget is dropped — for swapping in over any of the eight.

Each slot covers the same four subjects in light and dark: the timer list, the timer
editor, widgets on their own panel, and widgets straight on the wallpaper.

## Sizes

| Asset | Size | Format |
| --- | --- | --- |
| Icon | 512×512 | 32-bit PNG, alpha |
| Feature graphic | 1024×500 | 24-bit PNG, no alpha |
| Phone | 1080×1920 (9:16) | 24-bit PNG, no alpha |
| 7-inch | 1920×1080 (16:9) | 24-bit PNG, no alpha |
| 10-inch | 2560×1440 (16:9) | 24-bit PNG, no alpha |

The generator reads every file back after writing it and fails the build if the
dimensions, the colour-component count or the byte size are outside what Play accepts,
so a wrong-sized asset cannot reach the upload form.

## How these were made, and what that means

There is no emulator anywhere in this project's toolchain — neither CI runners nor a
typical container expose `/dev/kvm`, and a software-rendered AVD is not usable. So the
screenshots are rendered on the JVM by Robolectric, which runs the real Android
framework, inflates the real layouts and draws real pixels. They are genuine renders of
the shipping code at the exact sizes above.

Two consequences worth knowing:

- **The status bar is drawn, not captured.** Robolectric has no system UI, so the clock,
  signal, wifi and battery across the top are painted from primitives. On the app screens
  this is closer to the truth than leaving it out: those layouts set
  `fitsSystemWindows="true"`, so on a device the content really is inset below a status
  bar, and the capture insets it the same way. The clock reads the same instant the
  widgets were rendered from, so it agrees with the countdown values beside it.
- **The home-screen shots are composites.** The widgets are the real `RemoteViews` the
  app ships, inflated by the framework at true cell sizes and showing all three of
  `WidgetRenderer`'s detail levels. Behind them is a photograph, cropped per slot — there
  is no launcher to photograph, so the wallpaper is the one part of the frame that did
  not come out of this app. The dark-theme shots dim it, which is what Android 12+ does
  to a wallpaper in dark theme, and is also what keeps the light widget text legible.
  There are no other apps' icons and no fake dock.

The icon bakes in fixed colours, which the shipped icon does not have.
`ic_launcher.xml` paints itself from `@android:color/system_accent1_100` and `_700`,
Material You framework resources that follow the user's wallpaper, so on a device the
icon has no single colour. What these assets use is the platform's baseline palette —
what a device with an unremarkable wallpaper shows.

## The home-screen shots are not MIT licensed

The wallpaper is an original photograph, and it is reserved rather than given away with
the rest of the repository. That means `0[5-8]-widgets-*.png` in each directory — twelve
files — are covered by [IMAGE-LICENSE](../IMAGE-LICENSE), not by
[LICENSE](../LICENSE). The other twenty-four screenshots contain no photograph and are
MIT like everything else.

## Known limitation: the tablet shots

The app has no `layout-sw600dp` resources, so on a 1280dp-wide tablet the phone layout
stretches: one very wide column of cards with a lot of empty space. The tablet
screenshots are honest about that, which is the problem — Play's large-screen guidelines
mark it down, and reviewers see these images first.

Adding a `layout-sw600dp/activity_main.xml` that caps the list's content width and
centres it would fix both the app and these screenshots cheaply. Until then, the other
option Play allows is to leave the tablet slots empty; they are not required.
