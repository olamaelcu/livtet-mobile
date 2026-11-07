package net.olamaelcu.livtet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Handing one book file to a reader app.
 *
 * KOReader is addressed explicitly when it is installed: another app that registers for EPUBs must
 * not silently take the handoff. When KOReader is absent, or will not accept the file, the reader
 * picks from the apps that do.
 *
 * The file lives in the shared Documents folder, which cannot be handed over as a `file://` path
 * (that throws on API 24+), so it goes out as a scoped `content://` URI from our [FileProvider]
 * with a read grant.
 */
object OpenInKoreader {
    private const val TAG = "OpenInKoreader"

    /** Open [filePath] in KOReader, or in whichever app the reader picks. */
    fun openBook(
        context: Context,
        filePath: String,
    ) {
        val file = File(filePath)
        if (!file.isFile) {
            Log.w(TAG, "book file is missing: $filePath")
            Toast.makeText(context, "That book file is gone", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = contentUri(context, file) ?: return
        val type = mimeTypeFor(file.name)

        val koreader = installedPackage(context)
        if (koreader != null && start(context, viewIntent(uri, type, koreader))) return

        val chooser =
            Intent.createChooser(viewIntent(uri, type, null), "Open with")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!start(context, chooser)) {
            Log.w(TAG, "no app accepts $type for $filePath")
            Toast.makeText(context, "No app can open this book", Toast.LENGTH_SHORT).show()
        }
    }

    /** A view of one already-granted URI, optionally addressed to one package. */
    private fun viewIntent(
        uri: Uri,
        type: String,
        targetPackage: String?,
    ): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, type)
            if (targetPackage != null) `package` = targetPackage
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** The shareable URI for [file], or null when the provider refuses. */
    private fun contentUri(
        context: Context,
        file: File,
    ): Uri? =
        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
            .getOrElse { error ->
                Log.w(TAG, "no content URI for ${file.path}", error)
                Toast.makeText(context, "That book cannot be shared", Toast.LENGTH_SHORT).show()
                null
            }

    /**
     * Start [intent], reporting whether anything accepted it.
     *
     * An absent target and an empty chooser both surface as `ActivityNotFoundException`, so the
     * caller decides what to tell the reader.
     */
    private fun start(
        context: Context,
        intent: Intent,
    ): Boolean =
        runCatching { context.startActivity(intent) }
            .onFailure { error -> Log.w(TAG, "could not start $intent", error) }
            .isSuccess

    /** The installed KOReader package, if any. */
    private fun installedPackage(context: Context): String? =
        KoreaderPresence.KO_READER_PACKAGES.firstOrNull { pkg ->
            runCatching {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            }
                .getOrDefault(false)
        }

    /** Content type by extension; KOReader keys its format handling off this. */
    private fun mimeTypeFor(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "epub" -> "application/epub+zip"
            "pdf" -> "application/pdf"
            "djvu" -> "image/vnd.djvu"
            "fb2" -> "application/x-fictionbook+xml"
            "mobi" -> "application/x-mobipocket-ebook"
            "azw3" -> "application/vnd.amazon.ebook"
            "cbz" -> "application/vnd.comicbook+zip"
            "cbr" -> "application/vnd.comicbook-rar"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
