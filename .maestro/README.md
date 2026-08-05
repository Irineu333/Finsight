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
│   └── smoke/
└── subflows/              # reusable pieces; run only when a flow calls them
    └── launch_fresh.yaml
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

## Selecting elements: test tags, never text

The app is localised, and the emulator's locale decides whether a button reads *Salvar* or *Save*.
So flows address elements by id:

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

Text selectors are still right for content the *user* produced — a transaction title typed by the
flow itself carries no translation.

### Naming

`snake_case`, describing the element rather than its screen position:
`add_transaction_save`, `bottom_navigation_bar`. Navigation items derive theirs from the route —
`NavDestination.name` turns `DashboardRoute` into `dashboard`, giving `nav_item_dashboard` — so a
tag can never drift from the destination it names.

Tag what a flow needs to touch, when it needs it. A tag with no flow behind it is dead weight that
still has to be kept correct.

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
