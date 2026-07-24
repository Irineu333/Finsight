plugins {
    `kotlin-dsl`
}

group = "com.neoutils.finsight.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.composeCompiler.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "finsight.kmp.library"
            implementationClass = "com.neoutils.finsight.convention.KmpLibraryConventionPlugin"
        }
        register("roomLibrary") {
            id = "finsight.room.library"
            implementationClass = "com.neoutils.finsight.convention.RoomLibraryConventionPlugin"
        }
        register("composeLibrary") {
            id = "finsight.compose.library"
            implementationClass = "com.neoutils.finsight.convention.ComposeLibraryConventionPlugin"
        }
        register("featureApi") {
            id = "finsight.feature.api"
            implementationClass = "com.neoutils.finsight.convention.FeatureApiConventionPlugin"
        }
        register("featureImpl") {
            id = "finsight.feature.impl"
            implementationClass = "com.neoutils.finsight.convention.FeatureImplConventionPlugin"
        }
        register("appShared") {
            id = "finsight.app.shared"
            implementationClass = "com.neoutils.finsight.convention.AppSharedConventionPlugin"
        }
    }
}
