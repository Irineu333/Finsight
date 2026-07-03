plugins {
    id("finsight.feature.api")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            implementation(projects.core.designsystem)
            implementation(libs.kotlinx.datetime)
            implementation(libs.arrow.core)
        }
    }
}
