plugins {
    kotlin("jvm")
}

group = "io.github.kotlinmania"
version = "0.1.1"

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}
