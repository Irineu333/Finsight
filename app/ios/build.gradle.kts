plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            linkerOpts += "-lsqlite3"
            binaryOption("bundleId", "com.neoutils.finsight.ComposeApp")

            // Export seletivo: apenas :core:* e :feature:*:api ficam visíveis ao Swift.
            // Os :feature:*:impl são linkados via :app:shared e permanecem invisíveis.
            export(projects.core.analytics)
            export(projects.core.auth)
            export(projects.core.common)
            export(projects.core.crashlytics)
            export(projects.core.database)
            export(projects.core.designsystem)
            export(projects.core.model)
            export(projects.core.navigation)
            export(projects.core.resources)
            export(projects.core.ui)
            export(projects.feature.accounts.api)
            export(projects.feature.budgets.api)
            export(projects.feature.categories.api)
            export(projects.feature.creditcards.api)
            export(projects.feature.dashboard.api)
            export(projects.feature.shell.api)
            export(projects.feature.recurring.api)
            export(projects.feature.report.api)
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

            api(projects.core.analytics)
            api(projects.core.auth)
            api(projects.core.common)
            api(projects.core.crashlytics)
            api(projects.core.database)
            api(projects.core.designsystem)
            api(projects.core.model)
            api(projects.core.navigation)
            api(projects.core.resources)
            api(projects.core.ui)

            api(projects.feature.accounts.api)
            api(projects.feature.budgets.api)
            api(projects.feature.categories.api)
            api(projects.feature.creditcards.api)
            api(projects.feature.dashboard.api)
            api(projects.feature.shell.api)
            api(projects.feature.recurring.api)
            api(projects.feature.report.api)
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
