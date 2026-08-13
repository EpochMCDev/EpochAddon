plugins {
    kotlin("jvm")
}

base {
    archivesName.set("plugin-two")
}

dependencies {
    compileOnly(project(":common"))
}
