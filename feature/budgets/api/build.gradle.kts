plugins {
    id("finsight.feature.api")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.model)
            api(projects.core.navigation)
            implementation(projects.core.designsystem)
            implementation(libs.kotlinx.datetime)
        }
    }
}
