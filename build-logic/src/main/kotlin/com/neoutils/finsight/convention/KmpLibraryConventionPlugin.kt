package com.neoutils.finsight.convention

import org.gradle.api.Plugin
import org.gradle.api.Project

class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        configureKotlinMultiplatform()
    }
}
