plugins {
    id("finsight.app.shared")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.analytics)
            api(projects.core.auth)
            api(projects.core.common)
            api(projects.core.crashlytics)
            api(projects.core.database)
            api(projects.core.designsystem)
            api(projects.core.model)
            api(projects.core.resources)
            api(projects.core.ui)

            api(projects.feature.accounts.api)
            implementation(projects.feature.accounts.impl)
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
            api(projects.feature.support.api)
            implementation(projects.feature.support.impl)
            api(projects.feature.transactions.api)
            implementation(projects.feature.transactions.impl)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.datetime)
        }
    }
}
