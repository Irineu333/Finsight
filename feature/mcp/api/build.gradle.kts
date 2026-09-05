plugins {
    id("finsight.feature.api")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            api(projects.core.navigation)
            implementation(projects.core.resources)
        }
    }
}
