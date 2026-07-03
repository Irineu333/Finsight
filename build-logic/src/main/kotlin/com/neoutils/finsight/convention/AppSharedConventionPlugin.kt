package com.neoutils.finsight.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.gradle.kotlin.dsl.configure

/**
 * Convenção do módulo agregador `:app:shared`: KMP library (Android library + JVM + iOS),
 * Compose, serialization e Koin. É o único módulo autorizado a depender de `feature:*:impl`,
 * regra verificada mecanicamente por [verifyAppSharedDependencyRules].
 */
class AppSharedConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        configureKotlinMultiplatform()
        configureCompose()
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.getByName("commonMain").dependencies {
                implementation(libs.findLibrary("kotlinx-serialization-json").get())
                implementation(libs.findLibrary("koin-core").get())
                implementation(libs.findLibrary("koin-compose").get())
                implementation(libs.findLibrary("koin-compose-viewmodel").get())
            }
            sourceSets.getByName("androidMain").dependencies {
                implementation(libs.findLibrary("koin-android").get())
            }
        }

        verifyAppSharedDependencyRules()
    }
}
