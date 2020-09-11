import org.jetbrains.kotlin.cli.common.toBooleanLenient

/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

plugins {
    kotlin("jvm")
    id("jps-compatible")
}

repositories {
    mavenLocal()
    maven {
        url = uri("${rootDir}/../PerfStatJvm/build/dist")
    }
}
val runtimePerfLib by configurations.creating {
    attributes {
        attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, objects.named("linux"))
        attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, objects.named(MachineArchitecture.X86_64))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("native-runtime"))
        attribute(CppBinary.OPTIMIZED_ATTRIBUTE, true)
    }
}

val usePerfStat = (project.findProperty("fir.bench.use.perf.stat") as? String)?.toBooleanLenient() == true

dependencies {
    Platform[193].orLower {
        testCompileOnly(intellijDep()) { includeJars("openapi", rootProject = rootProject) }
    }

    testCompileOnly(intellijDep()) {
        includeJars("extensions", "idea_rt", "util", "asm-all", rootProject = rootProject)
    }

    Platform[192].orHigher {
        testCompileOnly(intellijPluginDep("java")) { includeJars("java-api") }
        testRuntimeOnly(intellijPluginDep("java"))
    }

    testRuntime(intellijDep())

    testCompile(commonDep("junit:junit"))
    testCompileOnly(project(":kotlin-test:kotlin-test-jvm"))
    testCompileOnly(project(":kotlin-test:kotlin-test-junit"))
    testCompile(projectTests(":compiler:tests-common"))

    testCompileOnly(project(":kotlin-reflect-api"))
    testRuntime(project(":kotlin-reflect"))
    testRuntime(project(":core:descriptors.runtime"))
    testCompile(projectTests(":compiler:fir:analysis-tests"))
    testCompile(project(":compiler:fir:resolve"))
    testCompile(project(":compiler:fir:dump"))

    val asyncProfilerClasspath = project.findProperty("fir.bench.async.profiler.classpath") as? String
    if (asyncProfilerClasspath != null) {
        testRuntimeOnly(files(*asyncProfilerClasspath.split(File.pathSeparatorChar).toTypedArray()))
    }

    testCompile("org.jetbrains.kotlin.perfstatjvm:perfstatjvm:1.0-SNAPSHOT")

    if (usePerfStat) {
        runtimePerfLib("org.jetbrains.kotlin.perfstatjvm:perf-event-lib:1.0-SNAPSHOT")
    }
}

sourceSets {
    "main" { projectDefault() }
    "test" { projectDefault() }
}

projectTest {
    systemProperties(project.properties.filterKeys { it.startsWith("fir.") })
    workingDir = rootDir
    jvmArgs!!.removeIf { it.contains("-Xmx") }
    maxHeapSize = "8g"
    dependsOn(":dist")

    if (usePerfStat) {
        systemProperty("fir.bench.perf.lib", runtimePerfLib.singleFile.absolutePath)
        println("-Dfir.bench.perf.lib=${runtimePerfLib.singleFile.absolutePath}")
    }
    run {
        val argsExt = project.findProperty("fir.modularized.jvm.args") as? String
        if (argsExt != null) {
            val paramRegex = "([^\"]\\S*|\".+?\")\\s*".toRegex()
            jvmArgs(paramRegex.findAll(argsExt).map { it.groupValues[1] }.toList())
        }
    }
}

testsJar()
