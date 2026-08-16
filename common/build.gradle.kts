plugins {
    kotlin("jvm")
    `java-library`
    id("com.gradleup.shadow")
}

base {
    archivesName.set("epochcommon")
}

dependencies {
    // 将 Kotlin 运行时打进 common，作为共享库供依赖 common 的插件使用
    implementation(kotlin("stdlib"))

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.papermc.paper:paper-api:${providers.gradleProperty("apiVersion").get()}.build.+")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveClassifier.set("plain")
    manifest {
        attributes["Automatic-Module-Name"] = "com.epochaddon.common"
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    manifest {
        attributes["Automatic-Module-Name"] = "com.epochaddon.common"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
