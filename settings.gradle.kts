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
include(":core:utils")
include(":core:platform")
include(":core:analytics")
include(":core:auth")
include(":core:ui")
include(":core:database")
include(":core:domain")
include(":feature:accounts:api")
include(":feature:accounts:impl")
include(":feature:categories:api")
include(":feature:categories:impl")
include(":feature:creditCards:api")
include(":feature:creditCards:impl")
include(":feature:transactions:api")
include(":feature:transactions:impl")
include(":feature:recurring:api")
include(":feature:recurring:impl")
include(":feature:installments:api")
include(":feature:installments:impl")
include(":feature:budgets:api")
include(":feature:budgets:impl")
include(":feature:report:api")
include(":feature:report:impl")
include(":feature:support:impl")
include(":feature:home:api")
include(":feature:home:impl")
include(":feature:dashboard:api")