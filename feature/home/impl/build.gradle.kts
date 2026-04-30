plugins {
    id("kmp-feature")
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.home.api)
            implementation(projects.core.ui)
            implementation(projects.core.utils)
            implementation(projects.core.analytics)
            implementation(projects.feature.dashboard.api)
            implementation(projects.feature.transactions.api)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.neoutils.finsight.feature.home.impl.resources"
    generateResClass = auto
}

android {
    namespace = "com.neoutils.finsight.feature.home.impl"
}
