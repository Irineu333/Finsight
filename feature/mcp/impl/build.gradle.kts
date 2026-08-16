plugins {
    id("finsight.feature.impl")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.database)
            implementation(projects.core.navigation)

            implementation(projects.feature.mcp.api)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.kotlinx.datetime)
        }
        jvmMain.dependencies {
            // The server and its transport are declared here and nowhere else: the JVM
            // desktop target is the only one with a process that owns a socket, and a
            // dependency reachable from `commonMain` would offer a server to platforms
            // that cannot have one. Being in `jvmMain` of an `impl` is also what puts
            // them on the desktop distribution's runtime classpath, which
            // `McpServerReachesTheDistributionTest` (`:app:desktop`) holds up.
            implementation(libs.mcp.kotlin.sdk.server)
            implementation(libs.ktor.server.cio)

            // What the user chose about the server outlives the process, and this is the
            // mechanism the app already keeps preferences in (design D11).
            implementation(libs.multiplatform.settings)

            // The agent's presentation surface, which exists only where the server does.
            // `:core:ui` is here for the *decisions* a transaction is presented by — the
            // leg a perspective reads, the end that denominates a cross-currency figure,
            // the item's sign rule — never for its display models, which carry Compose
            // types an agent has no use for (`presentation-mapping`).
            implementation(projects.core.common)
            implementation(projects.core.ledger)
            implementation(projects.core.model)
            implementation(projects.core.ui)
            implementation(libs.kotlinx.datetime)

            // The domain the tools consume. Every one of these is a feature's `api`: the use cases
            // that own the rules and the repositories that hold the facades. Nothing of an `impl`
            // is reachable from here, which is what keeps a tool from re-deciding what a screen
            // decides — it can only call the same owner the screen calls.
            implementation(projects.feature.accounts.api)
            implementation(projects.feature.budgets.api)
            implementation(projects.feature.categories.api)
            implementation(projects.feature.creditcards.api)
            implementation(projects.feature.recurring.api)
            implementation(projects.feature.report.api)
        }
        jvmTest.dependencies {
            // A `Settings` the test states, never the developer's own: the tests below turn
            // the server on and mint tokens, and doing that to the machine's real
            // preferences would leave a secret behind and decide the next run's outcome.
            implementation(libs.multiplatform.settings.test)
        }
    }
}
