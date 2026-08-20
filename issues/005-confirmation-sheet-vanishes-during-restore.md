# The confirmation sheet can be dismissed while the restore is running, taking all feedback with it

**Severity:** low-medium — the user is left with no signal during an irreversible operation

## What

`ConfirmRestoreModal`
(`feature/backup/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/confirmRestore/ConfirmRestoreModal.kt`)
does not disable dismissal while the replacement runs. A tap on the scrim, or a back
gesture, reaches `ModalManager.dismiss(modal)` → `onDismissed()` → `onDiscard()` →
`BackupViewModel.discardCandidate()`
(`.../ui/screen/backup/BackupViewModel.kt:203-216`), which returns early because
`isRestoring` is true.

The file is safe, and the swap is not interrupted — it is one transaction and cannot be
called off. But `uiState.confirmation` stays non-null while the sheet is gone, and the
`DisposableEffect` in `BackupScreen` is keyed on the same modal, so nothing puts it back.

## How to reach it

Confirm a restore and tap outside the sheet before it finishes.

## What it costs

The sheet disappears, the spinner with it, and the person is left looking at the backup
screen with nothing happening, in the middle of the one operation in this app that cannot
be undone. When it lands, the screens simply change under them.

The buttons are already disabled during the swap, on the reasoning that there is nothing to
cancel — the same reasoning applies to the scrim, and it was not carried there.

## The fix

Disable scrim and back dismissal while `isRestoring`, the way the buttons already are.
