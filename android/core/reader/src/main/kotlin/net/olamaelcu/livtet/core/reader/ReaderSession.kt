package net.olamaelcu.livtet.core.reader

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/// Why a file could not be read as a publication.
sealed class ReaderError(message: String) : Exception(message) {
    /// The path does not name a readable file.
    class MissingFile(path: String) : ReaderError("no book file at $path")

    /// The file is there but the toolkit could not read it.
    class Unreadable(path: String, reason: String?) :
        ReaderError("could not read $path: ${reason ?: "unknown reason"}")

    /// The file was read but did not parse into a publication.
    class Unsupported(path: String, reason: String?) :
        ReaderError("could not open $path: ${reason ?: "unknown reason"}")
}

/**
 * One publication open for reading.
 *
 * Owns the [`Publication`] the toolkit parsed and the navigator factory that
 * renders it. The session is UI-free on purpose: the app supplies the fragment
 * host, so this module stays testable without an Activity and the reading
 * screen can be rebuilt without touching how a file is opened.
 *
 * Books reach this class one way: a peer offered one, `syncOfferAccept` wrote it
 * to `booksDir` and the store recorded the path on the edition. [open] takes that
 * path verbatim; it never fetches.
 */
class ReaderSession private constructor(
    /// The parsed publication: metadata, table of contents, reading order.
    val publication: Publication,
    /// The file it was opened from.
    val file: File,
) {
    /// The title the publication declares, if any.
    val title: String?
        get() = publication.metadata.title

    /// Whether this is an EPUB, i.e. whether the EPUB navigator can render it.
    val isEpub: Boolean
        get() = publication.conformsTo(Publication.Profile.EPUB)

    /// The factory that builds and restores the EPUB navigator.
    ///
    /// Null for a publication the EPUB navigator cannot render; the caller must
    /// check [isEpub] before offering a reading screen.
    val navigatorFactory: EpubNavigatorFactory? by lazy {
        if (isEpub) EpubNavigatorFactory(publication) else null
    }

    /**
     * How many resources the reading order has.
     *
     * A publication with none opens to a blank screen, so the reading screen
     * treats zero as "nothing to show here" rather than rendering an empty host.
     */
    val readingOrderSize: Int
        get() = publication.readingOrder.size

    /** Where to resume: the stored position, when it still parses and fits. */
    fun resumeAt(storedJson: String?): org.readium.r2.shared.publication.Locator? =
        ReadingPosition.locator(storedJson)

    companion object {
        /**
         * Parse the book at [path] into a [ReaderSession].
         *
         * Runs the toolkit's own EPUB parser: it is the step that proves a
         * received file is a book this app can actually show, rather than a
         * file whose name ends in `.epub`.
         */
        suspend fun open(context: Context, path: String): ReaderSession =
            withContext(Dispatchers.IO) {
                val file = File(path)
                if (!file.isFile) throw ReaderError.MissingFile(path)

                val httpClient = DefaultHttpClient()
                val retriever = AssetRetriever(context.contentResolver, httpClient)
                val opener =
                    PublicationOpener(
                        DefaultPublicationParser(
                            context,
                            httpClient = httpClient,
                            assetRetriever = retriever,
                            // No PDF support: nothing this app receives is a PDF, and
                            // the Pdfium adapter would link a second renderer.
                            pdfFactory = null,
                        ),
                    )

                val asset =
                    retriever
                        .retrieve(file)
                        .fold(
                            onSuccess = { it },
                            onFailure = { throw ReaderError.Unreadable(path, it.toString()) },
                        )
                val publication =
                    opener
                        .open(asset, allowUserInteraction = false)
                        .fold(
                            onSuccess = { it },
                            onFailure = { throw ReaderError.Unsupported(path, it.toString()) },
                        )

                ReaderSession(publication, file)
            }
    }
}
