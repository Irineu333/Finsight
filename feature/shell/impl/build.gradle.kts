plugins {
    id("finsight.feature.impl")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.navigation)
            implementation(projects.core.designsystem)
            implementation(projects.core.resources)
            implementation(projects.core.analytics)

            implementation(projects.feature.shell.api)
            implementation(projects.feature.dashboard.api)
            implementation(projects.feature.transactions.api)
            implementation(projects.feature.accounts.api)
            implementation(projects.feature.budgets.api)
            implementation(projects.feature.categories.api)
            implementation(projects.feature.creditcards.api)
            implementation(projects.feature.recurring.api)
            implementation(projects.feature.report.api)
            implementation(projects.feature.support.api)

            implementation(libs.compose.material3.adaptive)
        }
    }
}
