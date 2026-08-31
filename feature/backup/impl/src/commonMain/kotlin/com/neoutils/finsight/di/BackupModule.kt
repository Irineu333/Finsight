package com.neoutils.finsight.di

import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.database.repository.RoomArchiveMark
import com.neoutils.finsight.database.snapshot.PreMigrationCopyTarget
import com.neoutils.finsight.domain.ledger.TransactionRemovalPrelude
import com.neoutils.finsight.domain.restore.ArchiveRestore
import com.neoutils.finsight.domain.vault.ArchiveMark
import com.neoutils.finsight.domain.vault.BackupVault
import com.neoutils.finsight.domain.vault.KeptCopyReader
import com.neoutils.finsight.domain.vault.StandingVaultOffer
import com.neoutils.finsight.domain.vault.VaultAppOpening
import com.neoutils.finsight.domain.vault.VaultFolder
import com.neoutils.finsight.domain.vault.VaultMigration
import com.neoutils.finsight.domain.vault.VaultPeriodicBackup
import com.neoutils.finsight.domain.vault.VaultPreMigrationCopy
import com.neoutils.finsight.domain.vault.VaultPreventiveBackup
import com.neoutils.finsight.domain.vault.VaultPreventiveCoverage
import com.neoutils.finsight.domain.vault.VaultSwitch
import com.neoutils.finsight.feature.backup.api.BackupEntry
import com.neoutils.finsight.feature.backup.api.DestructiveAction
import com.neoutils.finsight.feature.backup.api.PeriodicBackup
import com.neoutils.finsight.feature.backup.api.PreventiveBackup
import com.neoutils.finsight.feature.backup.api.PreventiveCoverage
import com.neoutils.finsight.feature.backup.api.VaultOffer
import com.neoutils.finsight.feature.backup.impl.BackupEntryImpl
import com.neoutils.finsight.ui.screen.backup.BackupViewModel
import com.neoutils.finsight.ui.screen.backupHistory.BackupHistoryViewModel
import com.neoutils.finsight.ui.screen.backup.service.OwnCopyCheck
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The file dialogs, the folder somebody points at, where copies land, and what the running
 * app knows about itself: everything this feature cannot state in common code.
 *
 * The destination each platform binds is the two rungs behind
 * [com.neoutils.finsight.domain.vault.VaultDestinations], never one of them: which rung is
 * in force is a preference, and a platform that bound a single rung would put that decision
 * somewhere the vault cannot see it.
 */
expect val backupPlatformModule: Module

val backupModule = module {
    includes(backupPlatformModule)

    // How settings gets these screens into its own graph. Nothing compiles against it —
    // the graph is assembled at runtime — so a missing binding would be a settings screen
    // that crashes the moment it is opened.
    single<BackupEntry> { BackupEntryImpl() }

    factory { OwnCopyCheck(verifier = get()) }

    // One instance, because it holds the observable the screen and the triggers read the
    // vault from — two would be two answers to the same question.
    single { BackupVaultRepository(settings = get()) }

    factory<ArchiveMark> { RoomArchiveMark(database = get()) }

    factory {
        BackupVault(
            vault = get(),
            archive = get(),
            destination = get(),
            database = get(),
            origin = get(),
            files = get(),
            clock = get(),
        )
    }

    // Turning the vault on, from wherever it is turned on from: the switch on the backup
    // screen and the offer beside a destructive confirmation. Bound once because taking the
    // first copy is what enabling *is* (design D8 decides whether one is owed), and a
    // caller writing the preference itself would be a vault turned on with nothing in it.
    factory { VaultSwitch(state = get(), vault = get()) }

    // What every other feature reaches the vault through, and the only binding of it: a
    // second implementation would be a second answer to which actions are worth a copy.
    factory<PreventiveBackup> { VaultPreventiveBackup(state = get(), vault = get()) }

    // What a destructive confirmation is told about its own action, so that it stops
    // calling a deletion permanent while the vault is about to copy the archive first.
    // Bound beside the trigger and off the same state: two answers to "is a copy kept" is
    // exactly the divergence design D7 forbids.
    factory<PreventiveCoverage> { VaultPreventiveCoverage(state = get()) }

    // The offer a destructive confirmation carries. Bound here because "has it been made
    // already" is the vault's state and not the asking screen's — a feature that decided
    // it for itself would offer again to somebody who already said no.
    factory<VaultOffer> { StandingVaultOffer(vault = get(), switch = get()) }

    // The occasion the shell announces — the app was opened. It is resolved there in a
    // `LaunchedEffect`, which no compiler checks, so `AppModulesTest` asserts the binding.
    // What the occasion is worth to the vault is `VaultAppOpening`'s: the link to the
    // folder is checked before the periodic trigger's own conditions are asked (task 11.7).
    factory<PeriodicBackup> {
        VaultAppOpening(
            folder = get(),
            periodic = VaultPeriodicBackup(state = get(), vault = get(), clock = get()),
        )
    }

    // One instance, because it holds the reading of the link that the screen shows and the
    // app's opening writes — two would have the screen watching an answer nobody updates.
    // It is also the one place that pairs the link with the chosen rung, which is what the
    // router and both screens read to know where the copies are actually going.
    single { VaultFolder(state = get(), folder = get()) }

    // Carrying the history to a destination somebody has just moved to. It resolves the
    // router rather than a rung, because it is the only thing that addresses both at once —
    // and the router is the one place that knows which two they are (design D13).
    factory { VaultMigration(state = get(), destinations = get(), files = get()) }

    // `:core:database`'s port, claimed here and nowhere else. Whoever assembles the
    // database asks it for a path and passes on what it says; this is what puts the copy
    // taken before a migration under the same switch as the other two triggers (design D1)
    // without that module learning a vault exists (design D11). Nobody claiming it would
    // compile, pass every test, and quietly stop protecting an update.
    factory<PreMigrationCopyTarget> {
        VaultPreMigrationCopy(state = get(), place = get())
    }

    // The ledger's removal prelude, claimed here. It is the port's only claimant, and the
    // ledger falls back to doing nothing when nobody registers one — so without this line
    // a transaction, an installment and an invoice are all removed with nothing kept back,
    // and no test of the ledger would notice. The action named is the one the ledger can
    // announce: what a removal is *about* is facade knowledge it does not carry, and
    // removing an installment or an invoice reaches this through the same two methods.
    factory<TransactionRemovalPrelude> {
        val preventive = get<PreventiveBackup>()
        TransactionRemovalPrelude {
            preventive.captureBefore(DestructiveAction.DELETE_TRANSACTION)
        }
    }

    // The one implementation of replacing the archive with a file's content, shared by the
    // screen that picks a file and the screen that lists the copies the vault kept. Two
    // bindings would be two decisions about when the person is asked.
    factory {
        ArchiveRestore(
            database = get(),
            verifier = get(),
            preventive = get(),
            vault = get(),
            files = get(),
        )
    }

    viewModel {
        BackupViewModel(
            database = get(),
            archiveRestore = get(),
            files = get(),
            destination = get(),
            captureOrigin = get(),
            vault = get(),
            switch = get(),
            folder = get(),
            migration = get(),
            modalManager = get(),
            clock = get(),
        )
    }

    // Reading one kept copy, through the destination's own contract and the same gate the
    // restore runs. Bound beside that flow rather than inside the screen because both are
    // one decision about what makes a copy readable — and because a second reader would be
    // the second way of opening a file that design D2 exists to prevent.
    factory { KeptCopyReader(destination = get(), files = get(), verifier = get()) }

    viewModel {
        BackupHistoryViewModel(
            destination = get(),
            files = get(),
            archiveRestore = get(),
            reader = get(),
            state = get(),
            folder = get(),
            vault = get(),
            modalManager = get(),
        )
    }
}
