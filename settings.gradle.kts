pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    includeBuild("build-logic")
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ireader"
include(":app")
include(":core:common")
include(":core:common-android")
include(":core:model")
include(":core:files")
include(":core:data")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")
include(":core:navigation")
include(":core:testing")
include(":core:work")
include(":core:reader:api")
include(":core:reader:runtime")
include(":engines:engine-common")
include(":engines:txt")
include(":engines:epub")
include(":engines:pdf")
include(":feature:library")
include(":feature:reader")
include(":feature:annotations")
include(":feature:search")
include(":feature:settings")
 
