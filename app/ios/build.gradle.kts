plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            linkerOpts += "-lsqlite3"
            binaryOption("bundleId", "com.neoutils.finsight.ComposeApp")

            // Export seletivo: apenas :core:*, :library:*:api e :feature:*:api ficam
            // visíveis ao Swift. Os `impl` são linkados via :app:shared, invisíveis.

            // Core
            export(projects.core.common)
            export(projects.core.database)
            export(projects.core.designsystem)
            export(projects.core.ledger)
            export(projects.core.model)
            export(projects.core.navigation)
            export(projects.core.resources)
            export(projects.core.ui)

            // Library
            export(projects.library.analytics.api)
            export(projects.library.auth.api)
            export(projects.library.crashlytics.api)

            // Feature
            export(projects.feature.accounts.api)
            export(projects.feature.budgets.api)
            export(projects.feature.categories.api)
            export(projects.feature.creditcards.api)
            export(projects.feature.dashboard.api)
            export(projects.feature.recurring.api)
            export(projects.feature.report.api)
            export(projects.feature.settings.api)
            export(projects.feature.shell.api)
            export(projects.feature.support.api)
            export(projects.feature.transactions.api)
        }
        iosTarget.compilerOptions {
            freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.app.shared)

            // Everything exported above has to be `api` here too, or the framework has
            // nothing to export. The three groups are the same ones, in the same order.

            // Core
            api(projects.core.common)
            api(projects.core.database)
            api(projects.core.designsystem)
            api(projects.core.model)
            api(projects.core.navigation)
            api(projects.core.resources)
            api(projects.core.ui)

            // Library
            api(projects.library.analytics.api)
            api(projects.library.auth.api)
            api(projects.library.crashlytics.api)

            // Feature
            api(projects.feature.accounts.api)
            api(projects.feature.budgets.api)
            api(projects.feature.categories.api)
            api(projects.feature.creditcards.api)
            api(projects.feature.dashboard.api)
            api(projects.feature.recurring.api)
            api(projects.feature.report.api)
            api(projects.feature.settings.api)
            api(projects.feature.shell.api)
            api(projects.feature.support.api)
            api(projects.feature.transactions.api)

            implementation(compose.runtime)
            implementation(compose.ui)

            implementation(libs.koin.core)
        }
        iosMain.dependencies {
            implementation(libs.gitlive.firebase.analytics)
            implementation(libs.gitlive.firebase.crashlytics)
        }
        all {
            languageSettings.enableLanguageFeature("ContextParameters")
        }
    }
}
