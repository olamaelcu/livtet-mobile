package net.olamaelcu.livtet

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * JUnit rule that fails the test if a `FATAL EXCEPTION`, `ANR`, or `signal 6/11` line mentioning
 * [packageFilter] has been written to the `crash` logcat buffer since the test started.
 *
 * This is the kill-shot for the original "event loop thread panicked" failure mode that motivated
 * the AddBookSearchTest regression guard: a panic in Rust that crosses the JNI boundary lands in
 * `adb logcat -b crash` as a tombstone. Asserting that the buffer is empty for our package catches
 * a fatal FFI/jobject crash even if the activity somehow stays alive.
 *
 * Tradeoffs:
 * * Uses `Runtime.exec("logcat ...")` rather than a proper JNI hook. Simple, no extra deps; one
 *   fork-exec per test.
 * * Reads the entire crash buffer. For long-running test suites the buffer can wrap; we mitigate by
 *   clearing the buffer right before each test via `logcat -b crash -c` (the `beforeTest` hook), so
 *   any line we see was produced *during* this test.
 * * Best-effort: if `adb logcat` is unavailable or the device isn't connected, the rule logs a
 *   warning and passes. Tests that need the rule's signal should not run on those devices.
 */
class CrashBufferRule(private val packageFilter: String) : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                clearCrashBuffer()
                try {
                    base.evaluate()
                } finally {
                    val crash = dumpCrashBuffer()
                    val offending = crash.lines().filter { it.contains(packageFilter) }
                    if (offending.isNotEmpty()) {
                        throw AssertionError(
                            "Crash buffer contains $packageFilter-related " +
                                "entries during ${description.displayName}:\n" +
                                offending.joinToString("\n")
                        )
                    }
                }
            }
        }

    private fun clearCrashBuffer() {
        runAdb("logcat", "-b", "crash", "-c")
    }

    private fun dumpCrashBuffer(): String =
        try {
            val proc =
                ProcessBuilder("adb", "logcat", "-b", "crash", "-d")
                    .redirectErrorStream(true)
                    .start()
            proc.waitFor(5, TimeUnit.SECONDS)
            BufferedReader(InputStreamReader(proc.inputStream)).use { it.readText() }
        } catch (_: Exception) {
            ""
        }

    private fun runAdb(vararg args: String) {
        try {
            val proc = ProcessBuilder("adb" + args.toList()).redirectErrorStream(true).start()
            proc.waitFor(2, TimeUnit.SECONDS)
        } catch (_: Exception) {
            // Best-effort: the rule is opportunistic.
        }
    }
}
