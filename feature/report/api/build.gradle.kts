plugins {
    id("finsight.feature.api")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The report's figures are `Σ entries` over the accounts a perspective
            // resolves to, so its public use case is stated in the ledger's own read
            // type. Naming a core from an `api` is legal — `:core:ledger` is a core,
            // not another feature.
            implementation(projects.core.ledger)
            implementation(projects.core.model)
            api(projects.core.navigation)
            implementation(libs.kotlinx.datetime)
        }
    }
}
