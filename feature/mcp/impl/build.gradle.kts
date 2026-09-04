plugins {
    id("finsight.feature.impl")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.database)
            implementation(projects.core.designsystem)
            implementation(projects.core.navigation)
            implementation(projects.core.resources)

            // The section asks one question of the ledger, and only one: whether the posting an
            // entry of the log points at is still there. The log carries no foreign key — it must
            // never keep a posting from being deleted — so the reference may name something that is
            // gone, and offering a door to it would open an empty detail.
            implementation(projects.core.ledger)

            implementation(projects.feature.mcp.api)

            // The platform axis, whose owner is the destination catalog: the entry point into this
            // section is hidden off the desktop, and the section itself reads the same rule rather
            // than restating it.
            implementation(projects.feature.shell.api)

            // Where an entry of the activity log leads. Each of these is a feature's `api`: the
            // route of the section that holds what an act created, and — for a posting — the same
            // detail every list in the app opens.
            implementation(projects.feature.accounts.api)
            implementation(projects.feature.budgets.api)
            implementation(projects.feature.categories.api)
            implementation(projects.feature.creditcards.api)
            implementation(projects.feature.recurring.api)
            implementation(projects.feature.transactions.api)
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

            // The client half of the same SDK, here for the same reason and not for a
            // remote one: the bridge from a stdio session to the server the open window
            // already holds is a client of this app's own loopback endpoint (design D8).
            implementation(libs.mcp.kotlin.sdk.client)

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
            implementation(projects.feature.transactions.api)

            // The one refusal of the domain that arrives as a throw rather than in an
            // `Either`: the copy owed before a removal could not be taken. A screen answers
            // it by asking the person; this surface has nobody to ask and no tool that
            // reaches the vault, so it translates the exception into the refusal that says
            // as much. Without the type there is nothing to catch, and the removal comes
            // back as "the operation could not be completed".
            implementation(projects.feature.backup.api)

            // Every write use case answers `Either`, so the type is on the signature of
            // everything the registration family calls.
            implementation(libs.arrow.core)
        }
        jvmTest.dependencies {
            // A `Settings` the test states, never the developer's own: the tests below turn
            // the server on and mint tokens, and doing that to the machine's real
            // preferences would leave a secret behind and decide the next run's outcome.
            implementation(libs.multiplatform.settings.test)

            // The engine the SDK's client needs under it. It is the JVM engine the app
            // already uses elsewhere (`feature/settings/impl`), and it is declared here
            // rather than in `jvmMain` because nothing in production yet opens a client:
            // the module that will is the bridge, and it brings its own.
            implementation(libs.ktor.client.okhttp)
        }
    }
}
