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

            // The contract speaks civil dates: a reference date, a period, the date a rate
            // is an observation about. `LocalDate` is the type the whole domain already
            // uses for them, and re-expressing it as a string at the boundary would put a
            // second date format in the app.
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            // The money payload is built by a suspending factory over the reducer.
            implementation(libs.kotlinx.coroutinesTest)
        }
        jvmMain.dependencies {
            implementation(libs.mcp.kotlin.sdk)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
        }
    }
}
