# E2E tests (Maestro)

End-to-end flows that drive the real app on a real device. They are the outermost ring of the test
pyramid: the unit suite (`./gradlew allTests`) owns behaviour, these own *the journey* — that the
screens, navigation, modals and persistence hold together when a person actually taps through them.

Keep them few and load-bearing. A flow that duplicates what a ViewModel test already proves costs a
minute of emulator time to tell you nothing new.

## Layout

```
.maestro/
├── config.yaml            # the workspace: which flows run, where the report goes
├── flows/                 # everything here runs as a test, one folder per area
│   ├── accounts/
│   ├── creditcards/
│   ├── dashboard/
│   ├── ledger/
│   └── smoke/
└── subflows/              # reusable pieces; run only when a flow calls them
    ├── launch_fresh.yaml
    ├── open_section.yaml
    └── record_transaction.yaml
```

`subflows/` sits outside the `flows/**` glob on purpose — that is what keeps a shared building block
from being executed as a test of its own.

## Running

```bash
scripts/e2e.sh                        # build, install, run everything
scripts/e2e.sh --skip-build           # reuse the installed APK — the fast loop while writing flows
scripts/e2e.sh --tags smoke           # only flows tagged `smoke`
scripts/e2e.sh .maestro/flows/smoke   # only a folder, or a single .yaml
```

Needs the Maestro CLI (`curl -Ls https://get.maestro.mobile.dev | bash`) and a booted emulator or an
attached device. `maestro studio` opens an inspector against the running app, and `maestro hierarchy`
dumps the accessibility tree — the fastest way to find out what a screen actually exposes.

In CI the suite runs from `.github/workflows/e2e-android.yml`: manually, or on a pull request the
moment it gets the `e2e` label.

If every flow dies instantly on `UNAVAILABLE: io exception`, the app is not the suspect. Maestro
records the device's active session in `~/.maestro/sessions`, and while an entry is there the CLI
assumes something else already set up its driver and skips doing so — then fails on the first
command with an error naming none of that ([#3065](https://github.com/mobile-dev-inc/maestro/issues/3065)).
Maestro Studio holds such an entry, and leaves it behind when it exits. Close Studio, then
`rm ~/.maestro/sessions`.

## The pinned device

The suite runs on one device: **API 36, `pixel_6` profile** (1080x2400, density 420). `scripts/e2e.sh`
boots the `finsight_e2e` emulator when nothing is attached, and refuses to run against a device on a
different API level. `.github/workflows/e2e-android.yml` pins the same pair.

This is not fussiness. The flows scroll to reach what is below the fold, so density and screen height
decide whether `scrollUntilVisible` finds a field or the run turns red — the add-transaction sheet
put its submit button below the fold on one profile and above it on another. The screen is part of
the contract.

It is a phone with a phone's keyboard: the ordinary on-screen one, and none of the
physical-keyboard affordances. Left alone the emulator drifts into the latter — Gboard replaces the
keyboard with a small floating toolbar, which overlays whatever sheet is open, and text typed into a
field beneath it is lost. Two things hold it in place, and both are needed: the AVD must not
advertise a keyboard lid (`hw.keyboard.lid = no`, read at boot, which `scripts/e2e.sh` sets before
starting it), and `show_ime_with_hard_keyboard` must stay `0`. Turning that setting *on* sounds like
asking for a keyboard and does the opposite: it is what invites the toolbar in.

Text still goes missing when a field is typed into before it has focus, which is why
`record_transaction` reads each field back after typing it — a dropped keystroke fails there rather
than screens later, as a button that will not submit.

Create it once:

```bash
$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd \
    -n finsight_e2e -d pixel_6 -k "system-images;android-36;google_apis_playstore;arm64-v8a"
```

`scripts/e2e.sh` closes the keyboard lid in its config the first time it boots it.

## The device speaks English

Every flow runs against a device set to English. `scripts/e2e.sh` checks `persist.sys.locale` and
refuses to run otherwise, because the alternative is every text assertion turning red for a reason
no failure message would name.

It is checked and never set: the property needs root and a framework restart, and `pm clear` — which
each flow performs on launch — wipes any per-app locale. So the device's language is a precondition
of the suite, not something a run can arrange for itself.

That is what makes it legitimate to assert a rendered figure. `$457.10` is a real assertion about
the ledger: it proves two writes were persisted, summed and read back. Prefer asserting the number
without its currency symbol (`457.10`), so the check survives a change of symbol but not a change
of value.

## Reaching elements: test tags, not labels

Pinning the language settles what a flow may *assert*. It does not make labels good *selectors* —
a label is copy, it gets reworded, and a renamed button should not break a test that never cared
about its wording. So flows address elements by id:

```yaml
- tapOn:
    id: "add_transaction_save"
```

That id is a `Modifier.testTag` in Compose. It reaches Maestro only because the composition root
publishes tags to the accessibility tree, via `Modifier.exposeTestTags()`
(`core/designsystem` — `ui/util/ExposeTestTags`). **A root has to opt in, and a modal sheet, dialog
or popup is its own root** — that is why the modifier is applied twice: on `App`'s `Surface` for the
app window, and in `ModalBottomSheet` for every sheet. A new kind of window needs its own call, or
its tags will be invisible with no error to explain why.

Text is the right selector for two things: content the flow itself typed (a transaction title), and
a value being verified (an amount, a balance). Both are the subject of the assertion rather than an
incidental way to find a widget.

### Naming

`snake_case`, describing the element rather than its screen position:
`add_transaction_save`, `bottom_navigation_bar`. Navigation items derive theirs from the route —
`NavDestination.name` turns `DashboardRoute` into `dashboard`, giving `nav_item_dashboard` — so a
tag can never drift from the destination it names.

Tag what a flow needs to touch, when it needs it. A tag with no flow behind it is dead weight that
still has to be kept correct.

A flow that already walks through two states should assert both. What the UI *offers* is as much a
rule as what it computes — an account with no history offers Delete and one with history offers
Archive — and a flow standing on both sides of that change gets the assertion nearly free. Look for
those before writing a second flow to reach the same place.

## Writing a flow

Start from a known state. `subflows/launch_fresh.yaml` clears app data and waits for the shell, so
no flow inherits the leftovers of whichever one ran before it:

```yaml
appId: com.neoutils.finsight
name: smoke_launch
tags:
  - smoke
---
- runFlow: ../../subflows/launch_fresh.yaml
```

A fresh install is not an empty app: the default account (*Carteira* / *Wallet*) is seeded, and the
dashboard already carries its full default layout — balances, accounts, cards, spending by category,
budgets, recents, quick actions (`GetDashboardPreferencesUseCase`).

Most of that layout starts below the fold. The dashboard is a `LazyColumn`, so a component that has
not been scrolled to **is not composed**, and neither `maestro hierarchy` nor an `assertNotVisible`
can tell that apart from a component that is not configured. Reach for `scrollUntilVisible` before
concluding something is missing, and check `GetDashboardPreferencesUseCase` before believing it.

Prefer `extendedWaitUntil` to a bare `assertVisible` right after an action that animates or loads;
prefer an assertion that states the intent to one that merely happens to be true.

Two gestures to know. The accounts screen is a horizontal pager, and the figures on it always belong
to the account in view — but **never swipe RIGHT to page back**: from the left edge that is the
system's back gesture, and it leaves the screen instead. Re-enter the section, which opens on the
first account. And on the dashboard, `back` returns you wherever the last scroll left it, so scroll
before asserting anything near the top.
