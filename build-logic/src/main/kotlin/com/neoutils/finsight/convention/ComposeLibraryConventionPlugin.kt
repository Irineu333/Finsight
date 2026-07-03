package com.neoutils.finsight.convention

import org.gradle.api.Plugin
import org.gradle.api.Project

class ComposeLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        configureKotlinMultiplatform()
        configureCompose()
    }
}
