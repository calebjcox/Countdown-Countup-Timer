# CLAUDE.md

Working notes for this repo. [README.md](README.md) explains what the app does and
why it is built the way it is; this file is about how to work on it.

## Comments describe the present

The code is commented discursively on purpose — the widget-sizing code is only
followable because of it — so this is not a rule against long comments. It is a
rule about tense.

**A comment may describe the past only where a reader who does not know it could
undo the code.** Everything else is written in the present tense, describing what
is true now.

Two comments of the same shape, opposite verdicts:

- *"The label range used to be four numbers, a name size and a footer size per
  band"* — the sentence after it already states the spec, so the history is a
  second telling of it. Delete it.
- *"Fixed, never weighted"* — a weighted slot is the obvious-looking
  simplification and it reintroduces a layout bug. That one is actionable, but it
  belongs in the code as a constraint on the style, not as a diary entry about the
  change that removed it.

So: before/after narration is useful for the length of one review and is bloat
afterwards. Whatever it was protecting gets restated as a rule, a constraint or an
invariant — the thing a reader has to know *before* they simplify it.

Two exceptions:

- **Test KDocs where the bug is the test's reason to exist.** `WidgetSpacingTest`,
  `WidgetVariantSizeTest`, `WidgetOrientationTest` and `WidgetNightModeTest` each
  document the regression they pin. Strip that and the test reads as arbitrary,
  which is an invitation to delete it. These want tightening, not cutting.
- **Pointers to issues.** `see issue #11` is one clause and the issue holds the
  whole story. The pointer stays; the retelling does not.

### Where the narrative goes instead

Not deleted — filed, in whichever of these the reader would actually reach for:

- **A specific change** → that change's commit message, which `git log -S` and
  `git blame` reach from the line in question. Prefer more, smaller commits for
  this reason: one message carrying the reasoning for one change is worth more
  than one summarising five.
- **A decision with a discussion behind it** → its issue, with a one-clause
  pointer from the code.
- **Something a reader needs *going forward*** → the code, as a present-tense
  constraint. If it is true of the repo rather than of one file, it goes in this
  file — but check first that it is not already stated where it applies.

The test is what a reader needs, not what is interesting. Git and the issue
tracker cost nothing until someone goes looking; this file is read in full, every
session, whether or not anyone needed it.

## Build and test

```
./gradlew :core:test              # the date math
./gradlew :app:testDebugUnitTest  # backup format, widget sizing (Robolectric)
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

CI runs the same commands on Java 21 — Robolectric's floor for the pinned Android
SDK. `core/` is pure Kotlin with no Android imports; anything that can be decided
without a device belongs there rather than in `app/`.
