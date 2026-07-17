plugins {
    id("finsight.feature.impl")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.designsystem)
            implementation(projects.core.ui)
            implementation(projects.core.resources)
            implementation(projects.core.analytics)
            implementation(projects.core.crashlytics)

            implementation(projects.feature.dashboard.api)
            implementation(projects.feature.shell.api)
            implementation(projects.feature.categories.api)
            implementation(projects.feature.budgets.api)
            implementation(projects.feature.accounts.api)
            implementation(projects.feature.creditcards.api)
            implementation(projects.feature.recurring.api)
            implementation(projects.feature.report.api)
            implementation(projects.feature.support.api)
            implementation(projects.feature.transactions.api)

            implementation(libs.arrow.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.multiplatform.settings)
            implementation("sh.calvin.reorderable:reorderable:3.0.0")
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}
