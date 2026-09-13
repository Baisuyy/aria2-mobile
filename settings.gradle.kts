pluginManagement {
    repositories {
        // 使用 dl.google.com 镜像：maven.google.com 在本网络受限，而 dl.google.com 可达
        google()
        maven { url = uri("https://dl.google.com/android/maven2") }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = uri("https://dl.google.com/android/maven2") }
        mavenCentral()
    }
}

rootProject.name = "aria2-mobile"
include(":app")