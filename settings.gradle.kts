rootProject.name = "finsight"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":composeApp")

// Core
include(":core:analytics")
include(":core:auth")
include(":core:common")
include(":core:crashlytics")
include(":core:database")
include(":core:designsystem")
include(":core:model")
include(":core:resources")
include(":core:ui")

// Features
include(":feature:accounts:api")
include(":feature:accounts:impl")
include(":feature:budgets:api")
include(":feature:budgets:impl")
include(":feature:categories:api")
include(":feature:categories:impl")
include(":feature:creditcards:api")
include(":feature:recurring:api")
include(":feature:recurring:impl")
include(":feature:report:api")
include(":feature:report:impl")
include(":feature:support:api")
include(":feature:support:impl")
include(":feature:transactions:api")
include(":feature:transactions:impl")