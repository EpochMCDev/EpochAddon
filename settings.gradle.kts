import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    // Slimefun 模块使用自己的版本目录（gradle/libs.versions.toml），
    // 作为子模块时统一在此加载
    versionCatalogs {
        create("libs") {
            from(files("Slimefun4/gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "EpochAddon"

include("common", "epoch-market", "plugin-two", "Slimefun4")
findProject(":epoch-market")!!.projectDir = file("EpochMarket")
