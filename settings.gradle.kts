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

include(":app")
include(":core:utils")
include(":core:platform")
include(":core:analytics")
include(":core:auth")
include(":core:ui")
include(":core:database")
include(":core:domain")
include(":core:sharedui")
include(":feature:accounts:api")
include(":feature:accounts:ui")
include(":feature:accounts:impl")
include(":feature:categories:api")
include(":feature:categories:ui")
include(":feature:categories:impl")
include(":feature:creditCards:api")
include(":feature:creditCards:ui")
include(":feature:creditCards:impl")
include(":feature:transactions:api")
include(":feature:transactions:ui")
include(":feature:transactions:impl")
include(":feature:recurring:api")
include(":feature:recurring:ui")
include(":feature:recurring:impl")
include(":feature:installments:api")
include(":feature:installments:ui")
include(":feature:installments:impl")
include(":feature:budgets:api")
include(":feature:budgets:ui")
include(":feature:budgets:impl")
include(":feature:report:api")
include(":feature:report:impl")
include(":feature:support:impl")
include(":feature:home:api")
include(":feature:home:impl")
include(":feature:dashboard:api")
include(":feature:dashboard:ui")
include(":feature:dashboard:impl")