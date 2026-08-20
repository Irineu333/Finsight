# Leaving the screen mid-operation leaves a copy of the archive on disk

**Severity:** medium-low — a full copy of the database survives, on an everyday gesture

## What

Three paths in
`feature/backup/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/backup/BackupViewModel.kt`
clean up a temporary file in a way that cancellation skips.

| Path | Line | Why it is skipped |
|---|---|---|
| Export | `:130` | `finally { files.discard(path) }` — `discard` is `suspend`, and a suspending call inside `finally` does not run once the coroutine is cancelled |
| Verification | `:157` | the candidate is discarded only on the `Rejected` branch; cancelling after the copy and before the decision leaves it |
| Restore | `:191` | `dropCandidate()` sits after the `try`, not inside a `finally`, so cancelling during the swap skips it entirely |

All three run in `viewModelScope`, which is cancelled when the screen goes away.

## Measured, not assumed

A probe with the same shape as `captureInto` — a suspending call inside `finally` — with the
job cancelled while it was suspended:

```
[probe] suspend call in finally ran after cancel? cleaned=false
```

`NonCancellable` appears nowhere in the project, so no path here is protected from this.

## How to reach it

Start an export and navigate back before it finishes.

## What it costs

A complete copy of the database in the app's cache (Android), temporary directory (iOS) or
`java.io.tmpdir` (desktop). Private to the app, and the system may reclaim it — Android's
`cacheDir` only under storage pressure. The data is the user's whole financial history, and
it outlives the operation that had a reason to write it.

## The fix

Wrap the cleanup in `withContext(NonCancellable)`, and move the restore's `dropCandidate()`
into a `finally`. The verification path needs the candidate discarded on every exit that
does not hand it to a confirmation, cancellation included.
