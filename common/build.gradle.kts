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
}

tasks.jar {
    enabled = false
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