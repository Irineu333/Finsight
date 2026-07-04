plugins {
    id("finsight.compose.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.designsystem)
            implementation(projects.core.model)
            implementation(projects.core.resources)

            implementation(libs.kotlinx.datetime)
        }
    }
}
