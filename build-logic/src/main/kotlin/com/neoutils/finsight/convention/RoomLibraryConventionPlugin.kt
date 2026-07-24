package com.neoutils.finsight.convention

import androidx.room.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Biblioteca KMP que declara entities e DAOs do Room. Carrega o encargo do KSP por target,
 * que é a parte da configuração com custo real de manutenção a cada target novo.
 */
class RoomLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        configureKotlinMultiplatform()

        with(pluginManager) {
            apply("com.google.devtools.ksp")
            apply("androidx.room")
        }

        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }

        val roomCompiler = libs.findLibrary("androidx-room-compiler").get()

        dependencies {
            listOf(
                "kspCommonMainMetadata",
                "kspAndroid",
                "kspJvm",
                "kspIosSimulatorArm64",
                "kspIosX64",
                "kspIosArm64",
            ).forEach { add(it, roomCompiler) }
        }
    }
}
