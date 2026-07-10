plugins {
    id("finsight.feature.api")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            api(projects.core.navigation)
        }
    }
}
