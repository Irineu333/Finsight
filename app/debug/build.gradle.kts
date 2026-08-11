plugins {
    id("finsight.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Ports only. This module replaces what a device cannot be asked for, and it names
            // the contracts it replaces — never a provider — which is the same rule every
            // feature's `impl` follows.
            implementation(projects.core.model)
            implementation(projects.feature.support.api)

            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
        }
    }
}
