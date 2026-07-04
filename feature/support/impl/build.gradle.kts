plugins {
    id("finsight.feature.impl")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.model)
            implementation(projects.core.analytics)
            implementation(projects.core.crashlytics)
            implementation(projects.core.designsystem)
            implementation(projects.core.resources)
            implementation(projects.feature.support.api)

            implementation(libs.gitlive.firebase.firestore)
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.arrow.core)
            implementation(libs.kotlinx.datetime)
        }
    }
}
