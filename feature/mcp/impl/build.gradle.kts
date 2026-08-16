plugins {
    id("finsight.feature.impl")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.navigation)

            implementation(projects.feature.mcp.api)
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
        }
    }
}
