plugins {
    id("finsight.feature.api")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            // Reading the ledger is legal from an `api`: `:core:ledger` is a core, not
            // another feature. It is what lets the overview's window be derived here
            // rather than handed in already computed (design D5).
            implementation(projects.core.ledger)
            implementation(projects.core.model)
            api(projects.core.navigation)
            implementation(projects.core.designsystem)
            implementation(libs.kotlinx.datetime)
            implementation(libs.arrow.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}
