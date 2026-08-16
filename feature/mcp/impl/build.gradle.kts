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
        }
        jvmTest.dependencies {
            // A `Settings` the test states, never the developer's own: the tests below turn
            // the server on and mint tokens, and doing that to the machine's real
            // preferences would leave a secret behind and decide the next run's outcome.
            implementation(libs.multiplatform.settings.test)
        }
    }
}
