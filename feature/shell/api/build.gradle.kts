plugins {
    id("finsight.feature.api")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.navigation)
            api(projects.core.designsystem)
        }
    }
}
