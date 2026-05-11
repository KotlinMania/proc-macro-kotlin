pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins { kotlin("multiplatform") version "2.3.21" }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0" }

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "proc-macro-kotlin"

// Local-dev convenience: when the proc-macro2-kotlin sibling is checked out
// next to this repo, substitute its included build for the published
// artifact so both libraries can move in lockstep without a publish-to-
// MavenLocal step. The `if` guard means CI (no sibling checkout) falls
// through to the published artifact.
//
// This block is a deliberate local convenience, not load-bearing. Drop or
// guard it differently if/when both libraries reach a stable
// published-only workflow.
val procMacro2Local = file("../proc-macro2-kotlin")
if (procMacro2Local.exists()) {
    includeBuild(procMacro2Local) {
        dependencySubstitution {
            substitute(module("io.github.kotlinmania:proc-macro2-kotlin")).using(project(":"))
        }
    }
}
