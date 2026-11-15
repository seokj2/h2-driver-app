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
        // 플레오스 Vehicle SDK를 붙일 때 여기에 추가한다(문서: document.pleos.ai, Connect SDK 시작하기):
        // maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
    }
}
rootProject.name = "H2Driver"
include(":core", ":app")
