plugins {
    id("finsight.feature.impl")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.analytics)
            implementation(projects.core.common)
            // The database is what captures, verifies and replaces its own content
            // (design D7); this module is what calls any of it "backup" and what turns
            // its refusals into a sentence a person reads.
            implementation(projects.core.database)
            implementation(projects.core.designsystem)
            implementation(projects.core.navigation)
            implementation(projects.core.resources)
            // `ArchiveReplacedHook`: a restore tells whichever facade claims it that a
            // device preference indexing the old archive's rows by id no longer describes
            // anything real. This module only calls the port; `:core:model` is where every
            // facade model already lives, `DashboardComponentPreference` included.
            implementation(projects.core.model)

            implementation(projects.feature.backup.api)

            implementation(libs.arrow.core)
            implementation(libs.kotlinx.datetime)
            // What the vault is — on, how often, how many copies, where, and when it last
            // succeeded — is a preference of this install and not a table: it must not
            // travel inside a backup file and come back in time with a restore (design D9).
            implementation(libs.multiplatform.settings)

            // The two entries of this screen are settings tiles, and the look they are
            // dressed in comes from `SettingsTileTheme` in `:core:designsystem`. Each
            // screen that renders tiles declares the library itself; nothing re-exports
            // it.
            implementation(libs.compose.settings.ui.tiles)
        }
        commonTest.dependencies {
            // The verification is exercised over real files, which means assembling a
            // real database — and that takes a currency seeding, which `:core:model`
            // declares.
            implementation(projects.core.model)
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.multiplatform.settings.test)
        }
    }
}
