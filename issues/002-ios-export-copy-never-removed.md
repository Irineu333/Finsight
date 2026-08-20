# Every export on iOS leaves a full copy of the archive behind

**Severity:** medium — unbounded accumulation of the user's financial data in the sandbox
**Platform:** iOS only

## What

`IosBackupFileService.copyOutCapturedFile`
(`feature/backup/impl/src/iosMain/kotlin/com/neoutils/finsight/backup/service/IosBackupFileService.kt:70-83`)
stages the capture into `NSTemporaryDirectory()/finsight-backup/<uuid>/<name>.db` and hands
that copy to the document picker, because the picker exports a file under its own name.

The staged copy is never deleted. `BackupViewModel.captureInto`
(`feature/backup/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/backup/BackupViewModel.kt:130`)
discards only the path it got from `newCapturePath()`; the staged one is internal to the
iOS service and nobody outside it knows the name.

The KDoc at `IosBackupFileService.kt:79` states "The staged copy is this app's to keep and
drop". Nothing drops it — the comment describes an intention, not the code.

`discard()` (`:93-99`) also removes files but never the per-call directory that
`createPrivateDirectory()` (`:200`) makes, so an empty directory is left behind by every
operation that does use it.

## How to reach it

Export twice. Two complete copies of the database sit under `NSTemporaryDirectory()`.

## What it costs

The whole ledger, in the clear, once per export, until iOS decides to reclaim `/tmp` — which
it may do when the app is not running, at no promised moment. It never leaves the app's
sandbox, so this is accumulation rather than exposure, but the data is the user's complete
financial history and nothing bounds how much of it piles up.

## The fix

Delete the staged copy once the picker has answered, in a `finally` so a refused or
cancelled export cleans up too — and remove the per-call directory with it. Note that a
`suspend` call in `finally` does not run under cancellation (see issue 003), so the cleanup
has to be reachable on that path as well.
