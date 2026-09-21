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
    }
}

rootProject.name = "cast"

include(":shared")

val protocolDir =
    System.getenv("ZOMBIE_PROTOCOL_DIR")?.let { file(it) }
        ?: file("../zombiebox-protocol").takeIf { it.isDirectory }
        ?: file(".deps/zombiebox-protocol")

require(protocolDir.resolve("android-shared").isDirectory) {
    "Restore zombiebox-protocol with make deps or set ZOMBIE_PROTOCOL_DIR"
}

project(":shared").projectDir = protocolDir.resolve("android-shared")
