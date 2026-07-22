plugins {
    id("finsight.feature.api")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            // Reading the ledger is legal from an `api`: `:core:ledger` is a core,
            // not another feature. It is what lets the spent figure be computed
            // here instead of handed in already computed (task 9.2).
            implementation(projects.core.ledger)
            implementation(projects.core.model)
            api(projects.core.navigation)
            implementation(projects.core.designsystem)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}
