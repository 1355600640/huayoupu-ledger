pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "HuimingLedger"
include(":app", ":core", ":paddle-ocr-sdk")
project(":paddle-ocr-sdk").projectDir = file(".tools/ocr-ab/sdk")
