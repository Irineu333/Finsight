plugins {
    id("finsight.feature.impl")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.database)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.designsystem)
            implementation(projects.core.ui)
            implementation(projects.core.resources)
            implementation(projects.core.analytics)
            implementation(projects.core.crashlytics)

            implementation(projects.feature.creditcards.api)
            implementation(projects.feature.transactions.api)
            implementation(projects.feature.categories.api)
            implementation(projects.feature.accounts.api)

            implementation(libs.arrow.core)
            implementation(libs.kotlinx.datetime)
        }
    }
}
