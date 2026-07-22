plugins {
    id("finsight.feature.api")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.model)
            // Temporary, for one slice (task 8.6): the eight features that read the
            // ledger still reach it through here, so the move can be verified on its
            // own before they each switch to `:core:ledger` in group 9.
            api(projects.core.ledger)
            api(projects.core.navigation)
            api(libs.androidx.navigation.compose)
            implementation(projects.core.designsystem)
            implementation(projects.core.ui)
            implementation(libs.kotlinx.datetime)
            implementation(libs.arrow.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}
