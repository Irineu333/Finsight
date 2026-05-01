plugins {
    id("kmp-feature")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.dashboard.api)
            implementation(projects.feature.transactions.api)
            implementation(projects.feature.categories.api)
            implementation(projects.core.database)
            implementation(projects.core.ui)
            implementation(projects.core.analytics)
            implementation(projects.core.utils)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.neoutils.finsight.feature.dashboard.impl.resources"
    generateResClass = auto
}

android {
    namespace = "com.neoutils.finsight.feature.dashboard.impl"
}
