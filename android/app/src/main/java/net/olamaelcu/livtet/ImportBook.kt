package net.olamaelcu.livtet

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.security.MessageDigest
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
 * task, and the reader expects a book to still be there next week. It carries its own metadata, so
 * the title and author come from the package rather than from its file name.
 */
object ImportBook {
    /** File extensions the reader can open, mapped to the format they are recorded as. */
    private val FORMATS = mapOf("epub" to "epub", "pdf" to "pdf")

    /** What an import produced. */
    data class Imported(
        val editionId: String,
        val title: String,
        val path: String,
        /** True when this exact file had already been imported and nothing was written. */
        val alreadyPresent: Boolean = false,
    )

    /**
     * Copy the file behind [uri] into the library and register it.
     *
     * Importing the same file twice is a no-op: the second pick resolves to the edition the first
     * one created. A phone and a desktop that both keep the same book is expected — the same file
     * arriving twice on one of them is not.
     */
    suspend fun from(context: Context, uri: Uri): Imported =
        withContext(Dispatchers.IO) {
            val displayName = displayName(context, uri)
            val extension = displayName.substringAfterLast('.', "").lowercase()
            val format =
                FORMATS[extension]
                    ?: throw IllegalArgumentException("$displayName is not an EPUB or PDF")

            val books = File(context.filesDir, "books").apply { mkdirs() }
            // Prefixed with a copy stamp so a second import of a different file with the
            // same name cannot overwrite the first one's bytes.
            val target = File(books, "${System.currentTimeMillis()}-$displayName")
            val digest =
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                    sha256(target)
                } ?: throw IllegalStateException("could not read $displayName")

            val manifest = ImportManifest(context)
            val existing = manifest.find(digest)
            if (existing != null && Bridge.editionFilePath(existing.editionId) != null) {
                // The copy that just landed is redundant; keep the one already recorded.
                target.delete()
                return@withContext Imported(
                    editionId = existing.editionId,
                    title = existing.title,
                    path = Bridge.editionFilePath(existing.editionId) ?: "",
                    alreadyPresent = true,
                )
            }

            val metadata = if (format == "epub") EpubMetadataReader.read(target) else null
            val title =
                metadata?.title?.takeIf { it.isNotBlank() }
                    ?: displayName.substringBeforeLast('.').ifBlank { displayName }
            val editionId =
                Bridge.importEdition(
                    title = title,
                    description = null,
                    contributors =
                        metadata
                            ?.author
                            ?.takeIf { it.isNotBlank() }
                            ?.let { listOf(ContributorInput(name = it, role = "author")) }
                            ?: emptyList(),
                    identifiers =
                        metadata?.identifier?.takeIf { it.isNotBlank() }?.let { listOf(it) }
                            ?: emptyList(),
                )
            Bridge.attachFile(
                editionId = editionId,
                path = target.absolutePath,
                format = format,
                sizeBytes = target.length(),
            )
            manifest.record(digest, editionId, title)
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

    /** Lowercase hex SHA-256 of a file, used as its identity for the import manifest. */
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Which content digest produced which edition.
 *
 * Kept beside the library rather than in the store: the store has no notion of a file's bytes, and
 * adding one is a migration in `core/`. A line per import, `digest<TAB>id<TAB>title`, is enough to
 * answer the only question asked of it.
 */
private class ImportManifest(context: Context) {
    private val file = File(context.filesDir, "import-manifest.tsv")

    data class Entry(val editionId: String, val title: String)

    fun find(digest: String): Entry? =
        read().firstOrNull { it.first == digest }?.let { Entry(it.second, it.third) }

    fun record(digest: String, editionId: String, title: String) {
        // Titles can contain tabs and newlines; the digest and the id cannot.
        val safeTitle = title.replace('\t', ' ').replace('\n', ' ')
        file.appendText("$digest\t$editionId\t$safeTitle\n")
    }

    /** `digest, editionId, title` per line, skipping anything unreadable. */
    private fun read(): List<Triple<String, String, String>> =
        runCatching {
                file
                    .takeIf { it.isFile }
                    ?.readLines()
                    .orEmpty()
                    .mapNotNull { line ->
                        val parts = line.split('\t')
                        if (parts.size < 3) null else Triple(parts[0], parts[1], parts[2])
                    }
            }
            .getOrDefault(emptyList())
}
