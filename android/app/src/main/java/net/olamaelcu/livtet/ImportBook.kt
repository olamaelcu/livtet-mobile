package net.olamaelcu.livtet

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bringing in a file the phone already holds.
 *
 * The desktop is the usual source of a library, but it is not the only one: a book can arrive
 * through a browser, a mail attachment, or a USB stick. This is the only door into the library that
 * needs no peer at all, which is why it is the one an empty shelf offers when nothing is paired.
 *
 * The picked file is copied rather than referenced: a `content://` URI is granted for the life of a
 * task, and the reader expects a book to still be there next week.
 */
object ImportBook {
    /** File extensions the reader can open, mapped to the format they are recorded as. */
    private val FORMATS = mapOf("epub" to "epub", "pdf" to "pdf")

    /** What an import produced. */
    data class Imported(val editionId: String, val title: String, val path: String)

    /**
     * Copy the file behind [uri] into the library and register it.
     *
     * The title comes from the file name, because nothing else knows it: the catalog is not
     * consulted here, so an import is instant and offline.
     */
    suspend fun from(context: Context, uri: Uri): Imported =
        withContext(Dispatchers.IO) {
            val displayName = displayName(context, uri)
            val extension = displayName.substringAfterLast('.', "").lowercase()
            val format =
                FORMATS[extension]
                    ?: throw IllegalArgumentException("$displayName is not an EPUB or PDF")

            val books = File(context.filesDir, "books").apply { mkdirs() }
            // Prefixed with a copy stamp so a second import of the same file name
            // cannot overwrite the first one's bytes.
            val target = File(books, "${System.currentTimeMillis()}-$displayName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("could not read $displayName")

            val title = displayName.substringBeforeLast('.').ifBlank { displayName }
            val editionId = Bridge.importEdition(title = title, description = null)
            Bridge.attachFile(
                editionId = editionId,
                path = target.absolutePath,
                format = format,
                sizeBytes = target.length(),
            )
            Imported(editionId = editionId, title = title, path = target.absolutePath)
        }

    /** The name the picker showed for this document, falling back to the path. */
    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) {
                    val name = cursor.getString(column)
                    if (!name.isNullOrBlank()) return name
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "untitled.epub"
    }
}
