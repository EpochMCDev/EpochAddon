plugins {
    kotlin("jvm")
    id("com.gradleup.shadow")
}

base {
    archivesName.set("EpochSkills")
}

dependencies {
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
        attributes["Automatic-Module-Name"] = "com.epochaddon.skills"
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    manifest {
        attributes["Automatic-Module-Name"] = "com.epochaddon.skills"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
