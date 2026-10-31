package net.olamaelcu.livtet

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A platform permalink the app knows how to render natively.
 *
 * These come from `https://livtet.olamaelcu.net/...` (Universal/App Links) or from the `livtet://`
 * scheme used for local verification and as the fallback when association is not verified.
 */
sealed interface AppLink {
    data class Book(val did: String, val rkey: String) : AppLink

    data class Review(val did: String, val rkey: String) : AppLink

    data class Profile(val actor: String, val tab: String, val shelf: String?) : AppLink
}

/** Navigation route for an [AppLink]; the graph declares these exact patterns. */
val AppLink.navigationRoute: String
    get() =
        when (this) {
            is AppLink.Book -> "book/$did/$rkey"
            is AppLink.Review -> "review/$did/$rkey"
            is AppLink.Profile ->
                buildString {
                    append("profile/").append(actor).append("?tab=").append(tab)
                    if (shelf != null) append("&shelf=").append(shelf)
                }
        }

/**
 * Parses platform permalinks into [AppLink]s and hands them to the navigation graph through
 * [pending].
 *
 * The rules are deliberately narrow: anything that is not one of the paths the platform publishes
 * returns null, so a link we cannot render never disturbs the app (the intent filter is broad
 * enough to catch near misses such as `/book/<did>` alone).
 */
object AppLinkRouter {
    const val SCHEME = "livtet"

    const val APP_LINK_HOST = "livtet.olamaelcu.net"

    /** The catalog's own DID, which links may spell as a readable alias. */
    const val BIBLIOGRAPH_ALIAS = "bibliograph"

    const val BIBLIOGRAPH_DID = "did:web:biblio.livtet.olamaelcu.net"

    const val TAB_REVIEWS = "reviews"
    const val TAB_READING = "reading"
    const val TAB_FINISHED = "finished"
    const val TAB_TO_READ = "to-read"
    const val TAB_ABANDONED = "abandoned"
    const val TAB_SHELVES = "shelves"

    /** Every tab the profile screen renders, in display order. */
    val TABS: List<String> =
        listOf(TAB_REVIEWS, TAB_READING, TAB_FINISHED, TAB_TO_READ, TAB_ABANDONED, TAB_SHELVES)

    /**
     * `bookShelving.metadata.status` for each reading-status tab. Status lives on the shelving
     * record, so a review tab has to join against it.
     */
    val TAB_STATUSES: Map<String, String> =
        mapOf(
            TAB_READING to "reading",
            TAB_FINISHED to "read",
            TAB_TO_READ to "to-read",
            TAB_ABANDONED to "dnf",
        )

    private const val MAX_SEGMENT_LENGTH = 512

    private val DID_PATTERN = Regex("^did:(plc|web):[A-Za-z0-9._:%-]+$")

    private val HANDLE_PATTERN = Regex("^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$")

    /** atproto record key charset, also used for shelf rkeys. */
    private val RKEY_PATTERN = Regex("^[A-Za-z0-9._~:-]{1,512}$")

    private val _pending = MutableStateFlow<AppLink?>(null)

    /** The link waiting to be navigated to; `DashboardActivity` collects it. */
    val pending: StateFlow<AppLink?> = _pending.asStateFlow()

    /** True when [raw] names a screen this app can render. */
    fun canHandle(raw: String?): Boolean = parse(raw) != null

    /** Parses [raw] and publishes it through [pending]; false when unhandled. */
    fun handle(raw: String?): Boolean {
        val link = parse(raw) ?: return false
        _pending.value = link
        return true
    }

    /** Clears [pending] after the graph has navigated to it. */
    fun consume() {
        _pending.value = null
    }

    /** Normalizes a link spelling of a DID: `bibliograph` → the catalog DID. */
    /** Clamps a `tab` query value, falling back to the reviews tab. */
    fun normalizeTab(value: String?): String =
        if (value != null && TABS.contains(value)) value else TAB_REVIEWS

    internal fun parse(raw: String?): AppLink? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val uri =
            try {
                URI(trimmed)
            } catch (_: URISyntaxException) {
                return null
            }
        val segments = splitPath(uri.rawPath ?: return null) ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        return when (scheme) {
            "https" -> {
                if (uri.host?.lowercase() != APP_LINK_HOST) return null
                val prefix = segments.firstOrNull() ?: return null
                parseKind(prefix, segments.drop(1), uri.rawQuery)
            }
            SCHEME -> {
                // `livtet://book/<did>/<rkey>` — the authority is the prefix.
                val prefix = uri.host?.lowercase() ?: return null
                parseKind(prefix, segments, uri.rawQuery)
            }
            else -> null
        }
    }

    private fun parseKind(kind: String, rest: List<String>, rawQuery: String?): AppLink? =
        when (kind) {
            "book" -> if (rest.size == 2) book(rest[0], rest[1]) else null
            "review" -> if (rest.size == 2) review(rest[0], rest[1]) else null
            "profile" ->
                when {
                    rest.isEmpty() -> null
                    rest.size == 1 -> profile(rest[0], queryParam(rawQuery, "tab"), null)
                    rest.size == 2 && rest[1] == "shelf" -> profile(rest[0], TAB_SHELVES, null)
                    rest.size == 3 && rest[1] == "shelf" -> profile(rest[0], TAB_SHELVES, rest[2])
                    else -> null
                }
            // `/u/<did>` 303s to `/profile/<did>` on the platform; no
            // association file advertises it, but shared links still exist.
            "u" -> if (rest.size == 1) profile(rest[0], null, null) else null
            else -> null
        }

    private fun book(did: String, rkey: String): AppLink? {
        val resolved = parseDid(did) ?: return null
        if (!RKEY_PATTERN.matches(rkey)) return null
        return AppLink.Book(resolved, rkey)
    }

    private fun review(did: String, rkey: String): AppLink? {
        val resolved = parseDid(did) ?: return null
        if (!RKEY_PATTERN.matches(rkey)) return null
        return AppLink.Review(resolved, rkey)
    }

    private fun profile(actor: String, tab: String?, shelf: String?): AppLink? {
        val resolved = parseProfileActor(actor) ?: return null
        if (shelf != null && !RKEY_PATTERN.matches(shelf)) return null
        return AppLink.Profile(resolved, normalizeTab(tab), shelf)
    }

    /** Reviews and books are owned by a repo, so the alias or a real DID is required. */
    private fun parseDid(value: String): String? =
        when {
            value == BIBLIOGRAPH_ALIAS -> BIBLIOGRAPH_DID
            DID_PATTERN.matches(value) -> value
            else -> null
        }

    /** Profiles are addressable by DID or handle; `/profile/{didOrHandle}`. */
    private fun parseProfileActor(value: String): String? =
        when {
            value == BIBLIOGRAPH_ALIAS -> BIBLIOGRAPH_DID
            DID_PATTERN.matches(value) -> value
            HANDLE_PATTERN.matches(value) -> value
            else -> null
        }

    /**
     * Percent-decodes each path segment. Blank segments (including the ones produced by a trailing
     * slash), decoded slashes, and oversized values are refused so a crafted link cannot smuggle a
     * different path or record key.
     */
    private fun splitPath(rawPath: String): List<String>? {
        val out = ArrayList<String>()
        for (part in rawPath.split('/')) {
            if (part.isEmpty()) continue
            val decoded = percentDecode(part) ?: return null
            if (decoded.isBlank()) return null
            if (decoded.contains('/')) return null
            if (decoded.length > MAX_SEGMENT_LENGTH) return null
            out.add(decoded)
        }
        return out
    }

    private fun queryParam(rawQuery: String?, name: String): String? {
        if (rawQuery.isNullOrEmpty()) return null
        for (pair in rawQuery.split('&')) {
            val index = pair.indexOf('=')
            val key = if (index < 0) pair else pair.substring(0, index)
            if (percentDecode(key) != name) continue
            val value = if (index < 0) "" else pair.substring(index + 1)
            return percentDecode(value)
        }
        return null
    }

    /**
     * Percent-decodes one component.
     *
     * Uses the `(String, String)` overload on purpose: the `(String, Charset)` one only exists from
     * API 33, and the app supports API 24. Malformed escapes and unsupported charsets both mean
     * "not a link we can read".
     */
    private fun percentDecode(value: String): String? =
        try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: Exception) {
            null
        }
}
