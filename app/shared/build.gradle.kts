plugins {
    id("finsight.app.shared")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.arrow.core)

            api(projects.core.analytics.api)
            implementation(projects.core.analytics.impl)
            api(projects.core.auth)
            api(projects.core.common)
            api(projects.core.crashlytics.api)
            implementation(projects.core.crashlytics.impl)
            api(projects.core.database)
            api(projects.core.designsystem)
            api(projects.core.model)
            api(projects.core.navigation)
            api(projects.core.resources)
            api(projects.core.ui)

            api(projects.feature.accounts.api)
            implementation(projects.feature.accounts.impl)
            api(projects.feature.shell.api)
            implementation(projects.feature.shell.impl)
            api(projects.feature.dashboard.api)
            implementation(projects.feature.dashboard.impl)
            api(projects.feature.budgets.api)
            implementation(projects.feature.budgets.impl)
            api(projects.feature.categories.api)
            implementation(projects.feature.categories.impl)
            api(projects.feature.creditcards.api)
            implementation(projects.feature.creditcards.impl)
            api(projects.feature.recurring.api)
            implementation(projects.feature.recurring.impl)
            api(projects.feature.report.api)
            implementation(projects.feature.report.impl)
            api(projects.feature.settings.api)
            implementation(projects.feature.settings.impl)
            api(projects.feature.support.api)
            implementation(projects.feature.support.impl)
            api(projects.feature.transactions.api)
            implementation(projects.feature.transactions.impl)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutinesTest)
            // A base currency the test decides, over a `Settings` that is not the
            // machine's: the gates below are about what the app shows for a given
            // base, and reading the developer's own preferences would decide it.
            implementation(libs.arrow.core)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.test)
        }
    }
}
