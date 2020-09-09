/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

import sun.management.VMManagement
import java.lang.management.ManagementFactory

internal class PerfHelper {
    private lateinit var process: Process
    private val perfOutputFile = createTempFile("perf")

    private fun getPID(): Int {
        val runtime = ManagementFactory.getRuntimeMXBean()
        val jvm = runtime.javaClass.getDeclaredField("jvm")
        jvm.isAccessible = true
        val mgmt = jvm[runtime] as VMManagement
        val pidMethod = mgmt.javaClass.getDeclaredMethod("getProcessId")
        pidMethod.isAccessible = true

        return pidMethod.invoke(mgmt) as Int
    }

    private fun getSubprocessPID(process: Process): Int {
        return process.javaClass.getDeclaredField("pid").also { it.isAccessible = true }.get(process) as Int
    }

    fun start() {

        process = ProcessBuilder().command("perf", "stat", "-p", getPID().toString(), "--per-thread", "-x", "\t", "-d")
            .redirectError(perfOutputFile)
            .redirectOutput(perfOutputFile)
            .also { println("Starting `${it.command().joinToString(" ")}`") }
            .start()

    }

    fun stop(): Result {
        val perfPid = getSubprocessPID(process)
        println("Stopping perf at $perfPid")
        Runtime.getRuntime().exec("kill -INT $perfPid")
        val exitCode = process.waitFor()

        return Result(exitCode, parsePerfOutput())
    }

    private fun parsePerfOutput(): List<MetricsRow> {
        return perfOutputFile.readLines().mapNotNull { line ->
            val parts = line.split('\t')
            val threadName = parts[threadNameCol]
            if (threadName.isNotBlank()) {
                MetricsRow(parts[metricNameCol], threadName, parts[metricValueCol], parts[unitCol].takeUnless { it.isBlank() })
            } else {
                null
            }
        }
    }

    data class MetricsRow(
        val metricName: String,
        val threadName: String,
        val value: String,
        val units: String?
    )

    data class Result(
        val exitCode: Int,
        val metrics: List<MetricsRow>
    )

    companion object {
        private const val threadNameCol = 0
        private const val unitCol = 2
        private const val metricNameCol = 3
        private const val metricValueCol = 1
    }

}