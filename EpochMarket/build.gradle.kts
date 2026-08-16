plugins {
    id("com.gradleup.shadow")
}

base {
    archivesName.set("EpochMarket")
}

repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    // CraftEngine and Slimefun are accessed reflectively at runtime, so their
    // server jars never become a build-time or class-loading requirement.

    implementation("org.xerial:sqlite-jdbc:3.47.2.0")

    testImplementation("io.papermc.paper:paper-api:${providers.gradleProperty("apiVersion").get()}.build.+")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    test {
        useJUnitPlatform()
    }
    shadowJar {
        archiveClassifier.set("")
    }
    build {
        dependsOn(shadowJar)
    }
}
