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

            implementation(projects.feature.home.api)
            implementation(projects.feature.dashboard.api)
            implementation(projects.feature.transactions.api)
        }
    }
}
