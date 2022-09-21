dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
rootProject.name = "SimpleMap"
// Define for library module
//include(":app")
// Define for local module
include(":app",":openchargemap")
project(":openchargemap").apply{
    projectDir = File(settingsDir,"../test/openchargemap_android_library/openchargemap")
}
 