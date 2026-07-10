plugins {
    id("finsight.feature.api")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.model)
            api(projects.core.navigation)
            api(libs.androidx.navigation.compose)
            implementation(projects.core.designsystem)
            implementation(projects.core.ui)
            implementation(libs.kotlinx.datetime)
            implementation(libs.arrow.core)
        }
    }
}
