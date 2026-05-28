import org.gradle.api.GradleException
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec
import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp)
    alias(libs.plugins.vanniktech)
}

group = providers.gradleProperty("project.group").getOrElse("io.github.kotlinmania")
version = providers.gradleProperty("project.version").getOrElse("0.1.0-SNAPSHOT")
val frameworkName = providers.gradleProperty("project.frameworkName").getOrElse("Unnamed")
val projectNamespace = providers.gradleProperty("project.namespace").getOrElse("io.github.kotlinmania")
val kotlinVersion = providers.gradleProperty("versions.kotlin").getOrElse("2.3.21")

// Gate Tier 3 targets behind opt-in properties — excluded from default build on CI.
// Set build.androidNative=true / build.intelSimulators=true in gradle.properties or -P to include.
val buildAndroidNative = providers.gradleProperty("build.androidNative").orNull?.toBoolean() ?: false
val buildIntelSimulators = providers.gradleProperty("build.intelSimulators").orNull?.toBoolean() ?: false

// Opt-ins shared between the top-level compilerOptions and the codeqlCompileJvm kotlinc invocation.
val commonOptIns =
    listOf(
        "kotlin.time.ExperimentalTime",
        "kotlin.concurrent.atomics.ExperimentalAtomicApi",
    )

// ============================================================================
// Android SDK installer
// ============================================================================
val androidCommandLineToolsRevision = providers.gradleProperty("android.commandLineTools.revision").getOrElse("14742923")
val projectCompileSdk = providers.gradleProperty("android.compileSdk").getOrElse("34")
val projectAndroidBuildTools = providers.gradleProperty("android.buildTools").getOrElse("36.0.0")
val osName = providers.systemProperty("os.name").get().lowercase()
val isWindowsHost = "windows" in osName
val isMacHost = "mac" in osName
val androidSdkOsName =
    when {
        isWindowsHost -> "win"
        isMacHost -> "mac"
        "linux" in osName -> "linux"
        else -> throw GradleException("Unsupported Android SDK setup OS: ${providers.systemProperty("os.name").get()}")
    }
val projectAndroidSdkDir = layout.projectDirectory.dir(".android-sdk").asFile
val androidSdkManager =
    projectAndroidSdkDir.resolve(
        if (isWindowsHost) {
            "cmdline-tools/latest/bin/sdkmanager.bat"
        } else {
            "cmdline-tools/latest/bin/sdkmanager"
        },
    )
val androidSdkInstallMarker = projectAndroidSdkDir.resolve(".install-complete")
val requiredAndroidSdkPackageDirs =
    listOf(
        projectAndroidSdkDir.resolve("platform-tools"),
        projectAndroidSdkDir.resolve("platforms/android-$projectCompileSdk"),
        projectAndroidSdkDir.resolve("build-tools/$projectAndroidBuildTools"),
    )

fun writeAndroidLocalProperties() {
    val sdkDirPropertyValue = projectAndroidSdkDir.absolutePath.replace("\\", "/")
    layout.projectDirectory
        .file("local.properties")
        .asFile
        .writeText("sdk.dir=$sdkDirPropertyValue\n")
}

fun isProjectAndroidSdkInstalled(): Boolean =
    androidSdkInstallMarker.exists() &&
        androidSdkManager.exists() &&
        requiredAndroidSdkPackageDirs.all { it.exists() }

fun sdkManagerCommand(vararg args: String): List<String> =
    if (isWindowsHost) {
        listOf("cmd", "/c", androidSdkManager.absolutePath) + args
    } else {
        listOf(androidSdkManager.absolutePath) + args
    }

val androidSdkExecOperations: ExecOperations = serviceOf<ExecOperations>()
val androidSdkArchiveOps: ArchiveOperations = serviceOf<ArchiveOperations>()
val androidSdkFsOps: FileSystemOperations = serviceOf<FileSystemOperations>()

fun installProjectAndroidSdk(execOps: ExecOperations) {
    if (isProjectAndroidSdkInstalled()) return

    val latestDir = projectAndroidSdkDir.resolve("cmdline-tools/latest")
    val markerFile = androidSdkInstallMarker
    val zipName = "commandlinetools-$androidSdkOsName-${androidCommandLineToolsRevision}_latest.zip"
    val url = "https://dl.google.com/android/repository/$zipName"

    println("setup-android-sdk: downloading $url")
    val tmpDir = projectAndroidSdkDir.resolve(".tmp/commandline-tools")
    tmpDir.deleteRecursively()
    tmpDir.mkdirs()
    val zipFile = tmpDir.resolve(zipName)

    URI(url).toURL().openStream().use { input ->
        Files.copy(input, zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    latestDir.deleteRecursively()
    latestDir.mkdirs()
    val canonicalLatestDir = latestDir.canonicalFile.toPath()

    ZipInputStream(zipFile.inputStream().buffered()).use { zipInput ->
        generateSequence { zipInput.nextEntry }.forEach { entry ->
            val relativeName = entry.name.removePrefix("cmdline-tools/").trimStart('/')
            if (relativeName.isNotEmpty()) {
                val target = latestDir.resolve(relativeName).canonicalFile
                if (!target.toPath().startsWith(canonicalLatestDir)) {
                    throw GradleException("Refusing to extract Android SDK entry outside $latestDir: ${entry.name}")
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile.mkdirs()
                    Files.copy(zipInput, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    if (!isWindowsHost && relativeName.startsWith("bin/")) {
                        target.setExecutable(true)
                    }
                }
            }
        }
    }

    tmpDir.deleteRecursively()

    val packages = arrayOf("platforms;android-$projectCompileSdk", "build-tools;$projectAndroidBuildTools", "platform-tools")
    println("setup-android-sdk: installing ${packages.joinToString()}")
    execOps.exec {
        commandLine(sdkManagerCommand("--sdk_root=$projectAndroidSdkDir", *packages))
        standardOutput = org.gradle.logging.LoggingOutputStream(logger, org.gradle.api.logging.LogLevel.LIFECYCLE)
        errorOutput = org.gradle.logging.LoggingOutputStream(logger, org.gradle.api.logging.LogLevel.ERROR)
    }

    markerFile.writeText("installed $(java.util.Date())")
    writeAndroidLocalProperties()
}

// Ensure SDK is installed before any Android compilation task runs.
tasks.matching { it.name.startsWith("compileAndroid") || it.name.startsWith("assembleAndroid") }.configureEach {
    dependsOn("ensureAndroidSdk")
}

if (!isProjectAndroidSdkInstalled()) {
    val ensureTask = tasks.register("ensureAndroidSdk") {
        outputs.file(androidSdkInstallMarker)
        doLast { installProjectAndroidSdk(androidSdkExecOperations) }
    }
    tasks.configureEach {
        if (name.startsWith("compileAndroid") || name.startsWith("assembleAndroid") || name == "testAndroidHostTest" || name == "assembleUnitTest") {
            dependsOn(ensureTask)
        }
    }
} else {
    tasks.register("ensureAndroidSdk") { doLast { println("setup-android-sdk: SDK already installed at $projectAndroidSdkDir") } }
    writeAndroidLocalProperties()
}

tasks.register("setupAndroidSdk") {
    group = "setup"
    description = "Downloads and configures the project-local Android SDK. (Alias for ensureAndroidSdk)"
    dependsOn("ensureAndroidSdk")
}

// ============================================================================
// Yarn security resolutions
// ============================================================================
val yarnResolutionDiff = providers.gradleProperty("yarn.resolution.diff").getOrElse("8.0.3")
val yarnResolutionFastUri = providers.gradleProperty("yarn.resolution.fast-uri").getOrElse("3.1.2")
val yarnResolutionSerializeJavascript = providers.gradleProperty("yarn.resolution.serialize-javascript").getOrElse("7.0.5")
val yarnResolutionWebpack = providers.gradleProperty("yarn.resolution.webpack").getOrElse("5.106.2")
val yarnResolutionFollowRedirects = providers.gradleProperty("yarn.resolution.follow-redirects").getOrElse("1.16.0")
val yarnResolutionLodash = providers.gradleProperty("yarn.resolution.lodash").getOrElse("4.18.1")
val yarnResolutionAjv = providers.gradleProperty("yarn.resolution.ajv").getOrElse("8.20.0")
val yarnResolutionBraceExpansion = providers.gradleProperty("yarn.resolution.brace-expansion").getOrElse("5.0.6")
val yarnResolutionFlatted = providers.gradleProperty("yarn.resolution.flatted").getOrElse("3.4.2")
val yarnResolutionMinimatch = providers.gradleProperty("yarn.resolution.minimatch").getOrElse("10.2.5")
val yarnResolutionPicomatch = providers.gradleProperty("yarn.resolution.picomatch").getOrElse("4.0.4")
val yarnResolutionQs = providers.gradleProperty("yarn.resolution.qs").getOrElse("6.15.2")
val yarnResolutionSocketIoParser = providers.gradleProperty("yarn.resolution.socket.io-parser").getOrElse("4.2.6")
val yarnResolutionWs = providers.gradleProperty("yarn.resolution.ws").getOrElse("8.20.1")
val yarnResolutionTmp = providers.gradleProperty("yarn.resolution.tmp").getOrElse("0.2.6")

rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    val yarnExtension = rootProject.extensions.getByType<YarnRootExtension>()
    yarnExtension.yarnLockMatchExactVersions = true
    fun yarnResolutions() = mapOf(
        "diff" to yarnResolutionDiff,
        "fast-uri" to yarnResolutionFastUri,
        "serialize-javascript" to yarnResolutionSerializeJavascript,
        "webpack" to yarnResolutionWebpack,
        "follow-redirects" to yarnResolutionFollowRedirects,
        "lodash" to yarnResolutionLodash,
        "ajv" to yarnResolutionAjv,
        "brace-expansion" to yarnResolutionBraceExpansion,
        "flatted" to yarnResolutionFlatted,
        "minimatch" to yarnResolutionMinimatch,
        "picomatch" to yarnResolutionPicomatch,
        "qs" to yarnResolutionQs,
        "socket.io-parser" to yarnResolutionSocketIoParser,
        "ws" to yarnResolutionWs,
        "tmp" to yarnResolutionTmp,
    )
    yarnResolutions().forEach { (pkg, ver) ->
        yarnExtension.resolution(pkg) {
            force(pkg, ver)
        }
    }
}

// ============================================================================
// JVM toolchain
// ============================================================================
val jvmToolchainVersion = providers.gradleProperty("jvm.toolchain").getOrElse("21").toInt()

kotlin {
    applyDefaultHierarchyTemplate()

    sourceSets.all {
        languageSettings.optIn("kotlin.time.ExperimentalTime")
        languageSettings.optIn("kotlin.concurrent.atomics.ExperimentalAtomicApi")
        languageSettings.optIn("kotlin.ExperimentalUnsignedTypes")
    }

    compilerOptions {
        // Vendored ANTLR4 runtime sources carry upstream warnings that are
        // intentional in their codebase. Leave -Werror off so the vendoring
        // drop stays bit-faithful.
        allWarningsAsErrors.set(false)
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    val xcf = XCFramework(frameworkName)

    macosArm64 {
        binaries.framework { baseName = frameworkName; xcf.add(this) }
    }
    iosArm64 {
        binaries.framework { baseName = frameworkName; xcf.add(this) }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = frameworkName
            isStatic = true
            xcf.add(this)
        }
    }
    if (buildIntelSimulators) {
        iosX64 {
            binaries.framework {
                baseName = frameworkName
                isStatic = true
                xcf.add(this)
            }
        }
    }

    tvosArm64 {
        binaries.framework { baseName = frameworkName; xcf.add(this) }
    }
    tvosSimulatorArm64 {
        binaries.framework { baseName = frameworkName; xcf.add(this) }
    }
    watchosArm64 {
        binaries.framework { baseName = frameworkName; xcf.add(this) }
    }
    watchosDeviceArm64 {
        binaries.framework { baseName = frameworkName; xcf.add(this) }
    }
    watchosSimulatorArm64 {
        binaries.framework { baseName = frameworkName; xcf.add(this) }
    }

    linuxX64()
    linuxArm64()
    mingwX64()

    if (buildAndroidNative) {
        androidNativeArm32()
        androidNativeArm64()
        androidNativeX86()
        androidNativeX64()
    }

    js {
        browser()
        nodejs()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmWasi {
        nodejs()
    }

    swiftExport {
        moduleName = frameworkName
        flattenPackage = projectNamespace
    }

    android {
        namespace = projectNamespace
        compileSdk = projectCompileSdk.toInt()
        minSdk = providers.gradleProperty("android.minSdk").getOrElse("24").toInt()
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder { sourceSetTreeName = "test" }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(jvmToolchainVersion.toString()))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(kotlin("stdlib"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// ============================================================================
// Test logging
// ============================================================================
tasks.withType<AbstractTestTask>().configureEach {
    testLogging {
        events(
            TestLogEvent.PASSED,
            TestLogEvent.FAILED,
            TestLogEvent.SKIPPED,
            TestLogEvent.STANDARD_OUT,
            TestLogEvent.STANDARD_ERROR,
        )
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

// ============================================================================
// Maven Publish POM
// ============================================================================
mavenPublishing {
    coordinates("io.github.kotlinmania", "antlr4-runtime", version.toString())
    pom {
        name.set("antlr4-runtime")
        description.set(providers.gradleProperty("project.pom.description").getOrElse("Kotlin Multiplatform port of ANTLR4 Runtime"))
        inceptionYear.set("2025")
        url.set("https://github.com/KotlinMania/proc-macro-kotlin")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("KotlinMania")
                name.set("KotlinMania")
                organization.set("KotlinMania")
                organizationUrl.set("https://github.com/KotlinMania")
            }
        }
        scm {
            url.set("https://github.com/KotlinMania/proc-macro-kotlin")
            connection.set("scm:git:git://github.com/KotlinMania/proc-macro-kotlin.git")
            developerConnection.set("scm:git:ssh://github.com/KotlinMania/proc-macro-kotlin.git")
        }
    }
}

// ============================================================================
// CodeQL compile helper — produces a classpath-only JAR for CodeQL extraction
// ============================================================================
val codeqlKotlinVersion = providers.gradleProperty("codeql.kotlin.version").getOrElse(kotlinVersion)
val codeqlSourceClasspath = providers.gradleProperty("project.dependencies.codeqlSourceClasspath").getOrElse("")
val codeqlAndroidAar = providers.gradleProperty("project.dependencies.codeqlAndroidAar").getOrElse("")

val codeqlCompileJvm by tasks.registering(JavaExec::class) {
    description = "Compile commonMain sources with kotlinc for CodeQL extraction"
    group = "codeql"
    val commonMainSrc = layout.projectDirectory.dir("src/commonMain/kotlin")
    val jvmMainSrc = layout.projectDirectory.dir("src/jvmMain/kotlin")
    val outDir = layout.buildDirectory.dir("codeql-classes")
    inputs.dir(commonMainSrc)
    inputs.dir(jvmMainSrc)
    outputs.dir(outDir)
    mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
    classpath = buildscript.configurations.getByName("kotlinCompilerClasspath")
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        buildList {
            add("-classpath")
            add(codeqlSourceClasspath)
            add("-d")
            add(outDir.get().asFile.absolutePath)
            add("-language-version")
            add(providers.gradleProperty("kotlin.languageVersion").getOrElse("2.3"))
            add("-api-version")
            add(providers.gradleProperty("kotlin.apiVersion").getOrElse("2.3"))
            add("-nowarn")
            commonOptIns.forEach { add("-opt-in=$it") }
            add(commonMainSrc.asFile.absolutePath)
            add(jvmMainSrc.asFile.absolutePath)
        }
    })
}

// ============================================================================
// Host-portable test runner
// ============================================================================
tasks.register("hostTests") {
    group = "verification"
    description = "Runs the host-portable real test suite (jvm, macosArm64, js, wasmJs, wasmWasi, android host)."
    dependsOn(
        listOf("jvmTest", "macosArm64Test", "jsNodeTest", "wasmJsNodeTest", "wasmWasiNodeTest", "testAndroidHostTest")
            .mapNotNull { tasks.findByName(it) },
    )
}

// Skip embedSwiftExportForXcode unless Xcode env is present
val xcodeSwiftExportEnvironmentNames =
    listOf(
        "SDK_NAME",
        "CONFIGURATION",
        "TARGET_BUILD_DIR",
        "BUILT_PRODUCTS_DIR",
        "ARCHS",
        "FRAMEWORKS_FOLDER_PATH",
        "DEPLOYMENT_TARGET_SETTING_NAME",
    )

fun hasXcodeSwiftExportEnvironment(): Boolean {
    val allPresent =
        xcodeSwiftExportEnvironmentNames.all {
            !providers.environmentVariable(it).orNull.isNullOrBlank()
        }
    if (!allPresent) return false
    val deploymentTarget = providers.environmentVariable("DEPLOYMENT_TARGET_SETTING_NAME").orNull ?: return false
    return !providers.environmentVariable(deploymentTarget).orNull.isNullOrBlank()
}

val swiftExportTaskDirectlyRequested =
    gradle.startParameter.taskNames.any {
        it == "embedSwiftExportForXcode" || it.endsWith(":embedSwiftExportForXcode")
    }

tasks.matching { it.name == "embedSwiftExportForXcode" }.configureEach {
    onlyIf("Xcode environment variables not present") {
        val hasXcodeEnvironment = hasXcodeSwiftExportEnvironment()
        if (!hasXcodeEnvironment && !swiftExportTaskDirectlyRequested) {
            logger.lifecycle("embedSwiftExportForXcode: skipped because Xcode environment variables are not present")
        }
        hasXcodeEnvironment || swiftExportTaskDirectlyRequested
    }
}

// ============================================================================
// `build` aggregate
// ============================================================================
val nativeTargetNames =
    buildList {
        if (buildAndroidNative) addAll(listOf("androidNativeArm32", "androidNativeArm64", "androidNativeX64", "androidNativeX86"))
        addAll(listOf("iosArm64", "iosSimulatorArm64"))
        if (buildIntelSimulators) add("iosX64")
        addAll(
            listOf(
                "linuxArm64",
                "linuxX64",
                "macosArm64",
                "mingwX64",
                "tvosArm64",
                "tvosSimulatorArm64",
                "watchosArm64",
                "watchosDeviceArm64",
                "watchosSimulatorArm64",
            ),
        )
    }

val fullTargetBuildTaskNames =
    buildSet {
        addAll(
            listOf(
                "compileAndroidMain",
                "compileAndroidHostTest",
                "compileAndroidDeviceTest",
                "assembleAndroidMain",
                "assembleUnitTest",
                "assembleAndroidTest",
                "assembleAndroidDeviceTest",
                "testAndroidHostTest",
                "jvmMainClasses",
                "jvmTestClasses",
                "jsMainClasses",
                "jsTestClasses",
                "wasmJsMainClasses",
                "wasmJsTestClasses",
                "wasmWasiMainClasses",
                "wasmWasiTestClasses",
                "embedSwiftExportForXcode",
                "assemble${frameworkName}XCFramework",
            ),
        )
        for (target in nativeTargetNames) {
            add("${target}Binaries")
            add("${target}TestBinaries")
        }
    }

tasks.named("build") {
    dependsOn(fullTargetBuildTaskNames)
    dependsOn(
        tasks.matching {
            name.endsWith("MainClasses") ||
                name.endsWith("TestClasses") ||
                name.endsWith("Binaries") ||
                name.endsWith("XCFramework") ||
                name == "embedSwiftExportForXcode" ||
                name.startsWith("exportCommonSourceSetsMetadataLocationsFor") ||
                name.startsWith("exportRootPublicationCoordinatesFor") ||
                name.startsWith("exportCrossCompilationMetadataFor") ||
                name.startsWith("exportTargetPublicationCoordinatesFor")
        },
    )
}

// Patch Wasm-WASI Node preopens
val patchWasmWasiNodePreopens = tasks.register("patchWasmWasiNodePreopens") {
    description = "Preopen the project directory for the generated Wasm-WASI Node test runner."
    group = "verification"
    dependsOn("compileTestDevelopmentExecutableKotlinWasmWasi")
    outputs.upToDateWhen { false }
    doLast {
        val runnerFile = layout.buildDirectory.file(
            "compileSync/wasmWasi/test/testDevelopmentExecutable/kotlin/${rootProject.name}-test.mjs",
        ).get().asFile
        if (!runnerFile.exists()) return@doLast
        val text = runnerFile.readText()
        val withCwdImport = text.replace(
            "import { argv, env } from 'node:process';",
            "import { argv, env, cwd } from 'node:process';",
        )
        val patched = withCwdImport.replace(
            "const wasi = new WASI({ version: 'preview1', args: argv, env, });",
            "const wasi = new WASI({ version: 'preview1', args: argv, env, preopens: { '/': cwd() }, });",
        )
        runnerFile.writeText(patched)
    }
}

tasks.named("wasmWasiNodeTest") {
    dependsOn(patchWasmWasiNodePreopens)
}
