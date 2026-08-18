plugins {
    kotlin("jvm")
    id("com.gradleup.shadow")
}

base {
    archivesName.set("EpochMinerals")
}

dependencies {
    implementation(kotlin("stdlib"))
    compileOnly(project(":common"))

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveClassifier.set("plain")
    manifest {
        attributes["Automatic-Module-Name"] = "com.epochaddon.minerals"
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    manifest {
        attributes["Automatic-Module-Name"] = "com.epochaddon.minerals"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
