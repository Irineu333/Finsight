package com.neoutils.finsight.domain.vault

import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup

/**
 * What came of asking the vault for a copy.
 *
 * The three ways of not producing one are told apart because their callers act on them
 * differently: a trigger that meets [VaultOff] or [AlreadyCovered] carries on as if it had
 * never asked — nothing is wrong and there is nothing to say — while [Failed] is the only
 * one that reaches a person, and is what makes a preventive capture able to stop the
 * destructive action it was protecting.
 */
sealed interface CaptureOutcome {

    /** A copy landed in the destination, and retention has already run over it. */
    data class Captured(val copy: StoredBackup) : CaptureOutcome

    /** The vault is off, so nothing was read, written or removed (design D1). */
    data object VaultOff : CaptureOutcome

    /**
     * The copy already in the destination still holds everything the archive does, so
     * another one would be the same file under a newer name (design D8).
     */
    data object AlreadyCovered : CaptureOutcome

    /**
     * No copy was written, and nothing was removed: retention only ever runs behind a
     * capture that landed (design D10).
     */
    data class Failed(val error: BackupError) : CaptureOutcome
}
