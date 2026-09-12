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
        google()
        mavenCentral()
        // JitPack لازم برای SDK های ایرانی (Poolakey + myket-billing-client)
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "SegaSimulator"
include(":app")
