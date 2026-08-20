# Verification calls unrelated failures "not this app's schema", and some escape entirely

**Severity:** low — needs a forged file to reach the worst of it, but the mislabelling is
everyday

## What

Two separate problems in the same area.

**The catch is too wide.** `CandidateVerifier.migrate`
(`core/database/src/commonMain/kotlin/com/neoutils/finsight/database/snapshot/CandidateVerifier.kt:91-103`)
turns every `Exception` into `SCHEMA_MISMATCH`, which the feature renders as "this file is
not a valid backup of this app". A full disk, an I/O error or a migration that aborted for
its own reasons all reach the user as a statement about the file. The user is told to pick a
different file for a problem no file will fix.

**Some failures are not caught at all.** Layer 4 documents itself as running "the migration
chain and the schema identity check", but when `user_version` already equals the app's,
Room does not validate the schema — it compares the identity hash and nothing else. So
`audit()` (`:114-125`) and `readCounts()` (`:231-236`) issue raw SQL against `entries`,
`dimensions`, `accounts`, `transactions`, `categories` and `credit_cards` on a file whose
tables nobody proved exist. `audit()` has no `catch`, so a `SQLiteException` there travels
out of `verify()`.

`BackupViewModel.chooseFileToRestore` (`:143-166`) handles only `Either` and
`CandidateVerification`, so that exception rises through `viewModelScope` — a crash on
Android. The same shape sits in `restore()` (`:185`, catching only
`DatabaseRestoreException`) and in `captureInto` (`:127`, only `DatabaseCaptureException`):
any other exception skips the cleanup and leaves the temporary behind (see issue 003).

## How to reach it

The mislabelling needs only a disk that fills during verification. The escaping exception
needs a file carrying this app's identity hash and a mutilated set of tables — forged, not
something a user stumbles into.

## The fix

Narrow the catch so the causes the user can act on keep their own names, and give `audit()`
the same treatment the earlier layers get: a failure there is a refusal, not an escape.
