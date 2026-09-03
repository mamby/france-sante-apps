pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (providers.gradleProperty("androidKitUseMavenLocal").getOrElse("false").toBoolean()) {
            mavenLocal {
                content {
                    includeGroup("net.mamby.androidkit")
                }
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "PersonalHealthVaultAndroid"
include(":app")
