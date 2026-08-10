package net.olamaelcu.livtet

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * Launchers for handing off to KOReader.
 *
 * Today only [launchKoreader] is wired up — it starts KOReader's home
 * screen so the user can pick a book from their KOReader library.
 *
 * [openBookInKoreader] is the richer "open *this* book in KOReader"
 * path. It is gated on the Rust FFI exposing a local file path or
 * content URI per `Book`; until that lands upstream, the data class in
 * `livtet_ffi.kt` only carries `id`/`title`/`description`, so there is
 * nothing to hand off. The helper is left in place so the wiring is
 * ready when the FFI grows the field.
 */
object OpenInKoreader {
    private const val TAG = "OpenInKoreader"

    /**
     * Launches KOReader's main activity. No-op if KOReader is not
     * installed (caller should already be gated on
     * [KoreaderPresence.isInstalled]). Shows a toast if no
     * KOReader package is currently launchable.
     */
    fun launchKoreader(context: Context) {
        val targetPkg =
            KoreaderPresence.KO_READER_PACKAGES.firstOrNull { pkg ->
                runCatching {
                    context.packageManager.getPackageInfo(pkg, 0)
                    true
                }.getOrDefault(false)
            }
        if (targetPkg == null) {
            Log.w(TAG, "launchKoreader called but KOReader not installed")
            return
        }
        val intent =
            context.packageManager.getLaunchIntentForPackage(targetPkg)
                ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no activity to launch $targetPkg", e)
            Toast.makeText(context, "KOReader not found", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * TODO(book-file-uri): wire once the FFI `Book` carries a
     * local file path or content URI per row. The current
     * generated `Book` (`livtet_ffi.kt`) has only id/title/description,
     * so there is no source URI to pass to KOReader's
     * `Intent.ACTION_VIEW` file handler. Once the Rust side exposes a
     * `localPath: String?` (or similar), build the intent here:
     *
     * ```
     * val intent = Intent(Intent.ACTION_VIEW).apply {
     *     setDataAndType(book.localPath.toUri(), mimeTypeFor(book))
     *     `package` = KoreaderPresence.KO_READER_PACKAGES.firstOrNull { isInstalled(it) }
     *     addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
     * }
     * ```
     *
     * Mime map: `.epub` → `application/epub+zip`, `.pdf` →
     * `application/pdf`, `.djvu` → `image/vnd.djvu`, `.fb2` →
     * `application/x-fictionbook+xml`, `.mobi` → `application/x-mobipocket-ebook`,
     * `.cbz` → `application/vnd.comicbook+zip`, `.txt` → `text/plain`.
     */
    @Suppress("unused")
    fun openBookInKoreader(context: Context, book: net.olamaelcu.livtet.ffi.Book) {
        Toast.makeText(context, "Open-in-KOReader needs the book file path (TODO)", Toast.LENGTH_SHORT).show()
    }
}
