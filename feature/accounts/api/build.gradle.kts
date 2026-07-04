plugins {
    id("finsight.feature.api")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.model)
            implementation(projects.core.designsystem)
            implementation(projects.core.resources)
            implementation(libs.kotlinx.datetime)
            implementation(libs.arrow.core)
        }
    }
}
