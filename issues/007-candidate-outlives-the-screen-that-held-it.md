# A candidate awaiting confirmation is left on disk when the screen goes away

**Severity:** medium-low — a full copy of the user's database survives, on an everyday gesture

**Found in:** while fixing `003-temp-files-survive-cancellation`. It is a different defect
that shares that one's symptom, and it is the part `try`/`finally` structurally cannot reach.

## What

Once the gate approves a candidate, the flow hands the path to a field and returns:

`feature/backup/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/backup/BackupViewModel.kt:163-164`

```kotlin
candidatePath = chosen
unclaimed = null
```

From there the only things that remove the file are `restore()` and `discardCandidate()`
(both through `dropCandidate()`, `:224-229`), and both need the user to answer the
confirmation sheet. There is no `onCleared()` — not in this view model and nowhere else in
the repository — so a view model that dies with the sheet still open takes the only
reference to the file with it. `candidatePath` (`:77`) is a plain field; nothing else in the
process knows the path.

`NonCancellable` does not help here and that is the point of a separate entry: the fix for
003 makes a removal survive cancellation, while here **no removal is ever started**.

## How to reach it

1. Settings → Backup → restore, pick a valid backup.
2. The confirmation sheet opens (the file has been copied in and approved).
3. Press back, or navigate away — the back arrow at `BackupScreen.kt:108` carries no `enabled` and is not gated
   on `uiState.isBusy`, so this is one tap.
4. The `NavBackStackEntry` is popped, its `ViewModelStore` is cleared, and the copy stays.

The copy is a full SQLite database of the whole ledger. Repeating the gesture leaves one
each time.

## The narrower sibling, same cause

`copyInChosenFile` creates the copy and returns its path in one step
(`JvmBackupFileService.kt:37-42`, and the same shape on the other two platforms). A
cancellation arriving while the copy is in flight makes `withContext` throw instead of
returning, so the file exists and the path is discarded unread. The view model cannot
close this one either: the path never reaches it.

## What the fix would be

Both point the same way: the file's lifetime belongs to whoever mints the path, not to the
screen that asked for it. `BackupFileService` already owns the private directory and both
creation points, and its KDoc already calls `discard` "best effort by design".

Two shapes worth weighing, in order of cost:

- **A sweep of the private directory when the service is constructed.** It is the only
  thing that ever collects a file orphaned by a process kill, which nothing above can. Note
  it does not work as-is on desktop today: `privateDirectory`
  (`JvmBackupFileService.kt:149-151`) is drawn anew per run, precisely so that no other
  local user can plant it, so a later run cannot find an earlier run's directory. A sweep
  needs a stable root that is verified rather than adopted, and that trade is the decision
  to make.
- **A bracket** — `suspend fun <T> useCandidate(block: suspend (String) -> T)` — so the
  path is never held across a lifetime the service does not control. It closes the two
  cases above and not the process-kill one.

The Koin binding is `factory` on all three platforms (`BackupModule.jvm.kt:10`,
`.android.kt:14`, `.ios.kt:10`), so a sweep would run once per service instance, not once
per process. Whichever shape wins should decide that too.
