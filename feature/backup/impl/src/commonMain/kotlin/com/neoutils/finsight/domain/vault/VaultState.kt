@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.vault

import com.neoutils.finsight.feature.backup.api.DestructiveAction
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The vault, at rest: whether it is on, how it is set, where it writes, and what it has
 * already done.
 *
 * It is one value rather than five preferences read one at a time, because every rule the
 * vault has is about a combination of them — whether a copy is due depends on [interval]
 * and [lastCapturedAt] together, and whether a copy is still enough depends on
 * [markAtLastCapture] and nothing else. A screen and a trigger reading the same snapshot
 * cannot disagree about what the vault is.
 *
 * **Off is what a fresh install is, and the default here is that promise** (spec: the vault
 * is born off). Every other default only ever applies to somebody who has turned it on.
 */
data class VaultState(
    /**
     * The one switch. Nothing the vault does happens without it (design D1), and the one
     * place that is enforced is [BackupVault].
     */
    val isOn: Boolean = false,

    /**
     * Whether the opening of the app is one of the occasions that may produce a copy.
     *
     * On by default, because turning the vault on puts all three triggers in force at once
     * with values nobody has to choose (spec: *ligar o cofre põe os três gatilhos em vigor*).
     * That is not a more permissive fallback: [isOn] governs every one of them, so this
     * being on decides nothing at all until somebody turns the vault on, and the two
     * triggers that can be switched off separately are switched off from a screen rather
     * than by never having been switched on.
     */
    val isPeriodicOn: Boolean = true,

    /**
     * Whether a destructive action is one of the occasions that may produce a copy.
     *
     * On by default, for the reason [isPeriodicOn] is: turning the vault on puts all three
     * triggers in force at once, and this decides nothing at all until somebody does
     * (spec: *ligar o cofre põe os três gatilhos em vigor*). It is the trigger that makes a
     * deletion reversible, so switching it off is a decision the sheet states rather than
     * one that could be arrived at by never having switched it on.
     *
     * It says *whether* the rule applies, never *which* actions it covers — that is
     * [com.neoutils.finsight.feature.backup.api.DestructiveClass]'s, and nothing here can
     * take an action out of a class (design D7).
     */
    val isPreventiveOn: Boolean = true,

    /**
     * How long a copy is allowed to be the newest one before the app looks for a reason to
     * take another, on the next opening — never a promise that one happens every so often,
     * which is a sentence no supported platform lets this app keep (design D5).
     */
    val interval: Duration = DEFAULT_INTERVAL,

    /**
     * How many copies the vault keeps, wherever it writes them (design D10) — including
     * the choice to remove nothing at all.
     */
    val retention: BackupRetention = BackupRetention.TEN,

    /** Which of the two rungs the copies are going to (design D3). */
    val destination: VaultDestination = VaultDestination.APP_STORAGE,

    /**
     * When the last capture that actually landed happened, or null when none ever has.
     *
     * Null is said as *never*, and deliberately not as some date: the screen's most
     * important line is this one — it is the only way a person finds out the protection
     * stopped (design D12) — and "never" and "a long time ago" lead to different actions.
     */
    val lastCapturedAt: Instant? = null,

    /**
     * How far the archive had got when that copy was taken, as [ArchiveMark] counts it, or
     * null when the mark could not be read at the time.
     *
     * It is the whole of the precondition in design D8, and it is here rather than beside
     * the copy in the destination for the reason the history is not a table either
     * (design D9): a value that travelled inside the file would come back in time with a
     * restore and start describing an archive that no longer exists.
     */
    val markAtLastCapture: Long? = null,

    /**
     * Which kept copy the archive in use is a copy of, or null when no copy describes it —
     * a picked file was restored, a restore did not land, or nothing has been captured yet.
     *
     * It is not [markAtLastCapture] under another name, and the two are apart precisely
     * when this one is needed: a restore gives coverage up so the next trigger captures,
     * and answers *nothing covers the archive* about an archive the person is very much
     * standing somewhere in. See [ArchiveCopy].
     */
    val archiveCopy: ArchiveCopy? = null,

    /**
     * Whether the offer beside a destructive confirmation has been left unticked and gone
     * past.
     *
     * It does not decide whether the offer appears — [isOfferable] does, and the offer
     * stands for as long as there is a vault to turn on. What it decides is the shape the
     * offer takes: an offer nobody has turned down arrives ticked, and one somebody has
     * arrives unticked and says so. Insisting would turn protection into nagging;
     * disappearing would take the offer out of the one place it means anything.
     *
     * It is recorded when the action goes ahead with the box unticked, and never when the
     * offer is merely shown: somebody who reads it and cancels has answered nothing.
     */
    val wasDeclined: Boolean = false,
) {

    /**
     * Whether a copy of the archive is genuinely taken before something destroys part of
     * it.
     *
     * Both switches, because both have to be on for that trigger to fire — and it is the
     * one thing a confirmation may not get wrong: promising the way back depends on the
     * copy being written (`local-backup` spec), so a sheet that stopped calling a restore
     * irreversible on a vault whose preventive trigger is off would be promising something
     * the app does not do.
     */
    val keepsCopy: Boolean get() = isOn && isPreventiveOn

    /**
     * Whether a copy is genuinely kept before [action] happens.
     *
     * It is [keepsCopy] and the action's class, which is the whole of the condition the
     * preventive trigger applies — the switches say *whether* the rule is in force, the
     * class says *which* actions it covers, and nothing here can take an action out of one
     * (design D7). Stated once, so that the sentence a confirmation shows and the copy the
     * trigger takes cannot come apart.
     */
    fun keepsCopyBefore(action: DestructiveAction): Boolean =
        keepsCopy && action.classification.isCoveredByPreventiveCapture

    /**
     * Whether there is anything to offer beside a destructive action: a vault that is off.
     *
     * There is no second half to this. An offer withdrawn after one refusal costs the only
     * discoverable way in — the switch is on a screen nobody visits — so what a refusal
     * changes is [wasDeclined] and the tone that reads it, never whether the offer is put.
     */
    val isOfferable: Boolean get() = !isOn

    /**
     * Whether [interval] has run out since the last copy that landed — the periodic
     * trigger's own question, and the screen's when it says a copy is overdue.
     *
     * A vault that has never captured is always due: there is nothing yet standing
     * between the archive and its loss.
     *
     * It is *not* the question of whether to capture. That one is [BackupVault]'s, and it
     * is about content rather than time (design D8): an interval that has run out with
     * nothing added since produces no copy at all.
     */
    fun isIntervalDue(now: Instant): Boolean {
        val last = lastCapturedAt ?: return true
        return now - last >= interval
    }

    /**
     * Whether the copy the screen names has been standing longer than the trigger that
     * would replace it allows — the sign that the protection may have stopped (design D12).
     *
     * It is [isIntervalDue] with the two conditions a *sign* has and a *decision to
     * capture* does not. A vault that has never captured is due but not late: the screen
     * says so in words of its own, and "never" and "a long time ago" lead to different
     * actions. And late is measured against the wait, which is the periodic trigger's own
     * question — with that trigger off nothing was going to capture on a clock, so a copy
     * standing while the vault captures only before deletions is exactly as recent as the
     * archive let it be.
     *
     * The comparison itself is not restated here, and that is the point: a second reading
     * of the same wait is a second reading that can disagree with the trigger's.
     */
    fun isLastCopyOverdue(now: Instant): Boolean =
        isPeriodicOn && lastCapturedAt != null && isIntervalDue(now)
}

/**
 * The two rungs of protection, in the order they cost the user something (design D3).
 *
 * Only the second one can be revoked, moved or emptied from outside the app, and only the
 * first one is guaranteed to be there — which is why the vault records which is in force
 * rather than inferring it from whether a folder was ever pointed at.
 */
enum class VaultDestination {

    /**
     * The app's own private storage. It is where the vault starts, it needs nothing from
     * the user, and it does not survive the app being uninstalled on either mobile
     * platform — a fact the screen states rather than hides.
     */
    APP_STORAGE,

    /** A folder the user pointed at, which outlives the app and can stop being reachable. */
    USER_FOLDER,
}

/**
 * Three days, which is high frequency paired with a short history on purpose: restoring is
 * all-or-nothing, so a three-week-old copy costs three weeks of entries and nobody uses it
 * (design D5).
 */
val DEFAULT_INTERVAL: Duration = 3.days
