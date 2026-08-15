plugins {
    id("finsight.app.mcp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.ledger)
            implementation(projects.core.model)

            implementation(projects.feature.accounts.api)
            implementation(projects.feature.budgets.api)
            implementation(projects.feature.categories.api)
            implementation(projects.feature.creditcards.api)
            implementation(projects.feature.mcp.api)
            implementation(projects.feature.recurring.api)
            implementation(projects.feature.transactions.api)
        }
        jvmMain.dependencies {
            implementation(libs.mcp.kotlin.sdk)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
        }
    }
}
