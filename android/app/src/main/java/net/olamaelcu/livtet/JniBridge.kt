package net.olamaelcu.livtet

/**
 * Holder for the FFI cdylib. uniFFI calls `System.loadLibrary` itself before invoking any FFI
 * stubs, so this object exists only to hold an explicit early `System.loadLibrary` call and to
 * give Kotlin a typed placeholder for any future native helpers.
 */
object JniBridge {
    init {
        // Defensive: uniffi already calls `System.loadLibrary` before
        // any FFI fires, but having an explicit loadLibrary here makes
        // load order obvious to readers and keeps the door open for
        // future JNI-side helpers without adding a new type.
        try {
            System.loadLibrary("livtet_ffi")
        } catch (_: UnsatisfiedLinkError) {
            // Already loaded — fine.
        }
    }
}
