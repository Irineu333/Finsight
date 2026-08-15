package com.neoutils.finsight.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.gradle.kotlin.dsl.configure

/**
 * Convention for `:app:mcp`, the MCP server: an app module **without UI** — no screen, no
 * route, no entry point — so Compose is deliberately absent.
 *
 * Its dependency rights are the ones of an `impl`: any `feature:*:api` plus `:core:*`, and
 * no `feature:*:impl`. That is literally rule 4, so the check is the very same
 * [verifyFeatureDependencyRules] the `impl` convention applies — a second copy of the rule
 * would diverge from the original at the first adjustment.
 */
class AppMcpConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        configureKotlinMultiplatform()
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.getByName("commonMain").dependencies {
                implementation(libs.findLibrary("kotlinx-serialization-json").get())
                implementation(libs.findLibrary("koin-core").get())
            }
        }

        verifyFeatureDependencyRules(isApi = false)
    }
}
