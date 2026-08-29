plugins {
    id("finsight.feature.impl")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.database)
            implementation(projects.core.ledger)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.designsystem)
            implementation(projects.core.ui)
            implementation(projects.core.resources)
            implementation(projects.core.analytics)
            implementation(projects.core.crashlytics)

            implementation(projects.feature.shell.api)

            implementation(projects.feature.recurring.api)
            implementation(projects.feature.categories.api)
            implementation(projects.feature.accounts.api)
            implementation(projects.feature.creditcards.api)
            implementation(projects.feature.budgets.api)
            // The summary card draws consolidated figures, and every one of them owes a
            // `ConsolidationBadge` with somewhere to lead: the rate archive lives here.
            implementation(projects.feature.settings.api)

            implementation(libs.arrow.core)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.turbine)
        }
    }
}
