plugins {
    id("kmp-feature")
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.ui)
            implementation(projects.core.analytics)
            implementation(projects.core.utils)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)

            // Firebase (used by FirebaseSupportRepository in androidMain/iosMain)
            implementation(libs.gitlive.firebase.firestore)
            implementation(libs.gitlive.firebase.auth)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.neoutils.finsight.feature.support.impl.resources"
    generateResClass = auto
}

android {
    namespace = "com.neoutils.finsight.feature.support.impl"
}
