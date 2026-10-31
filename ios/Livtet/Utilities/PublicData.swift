import Foundation

/// Everything a card needs about the edition a review points at.
struct BookSummary: Identifiable, Hashable {
    let uri: String
    let title: String
    let author: String?
    let coverURL: URL?

    var id: String { uri }
}

/// Reading-status counts for a profile header.
struct ProfileStats: Equatable {
    var total = 0
    var reading = 0
    var finished = 0
    var toRead = 0
    var abandoned = 0
}

/// One row of the Shelves tab.
struct ShelfSummary: Identifiable, Hashable {
    let rkey: String
    let name: String
    let bookCount: Int

    var id: String { rkey }
}

/// One book on the selected shelf.
struct ShelfBookRow: Identifiable, Hashable {
    let shelfRkey: String
    let bookUri: String?
    let title: String
    let status: String?

    var id: String { "\(shelfRkey)/\(bookUri ?? title)" }
}

/// Reading-status counts derived from an actor's shelving records.
func shelvingStats(_ shelving: [BiblioShelving]) -> ProfileStats {
    var reading = 0
    var finished = 0
    var toRead = 0
    var abandoned = 0
    for entry in shelving {
        switch entry.value.metadata?.status {
        case "reading": reading += 1
        case "read": finished += 1
        case "to-read": toRead += 1
        case "dnf": abandoned += 1
        default: break
        }
    }
    return ProfileStats(
        total: reading + finished + toRead + abandoned,
        reading: reading,
        finished: finished,
        toRead: toRead,
        abandoned: abandoned
    )
}

/// Shelf rkey → count of shelved books, keyed off the shelving record's shelf.
func shelfCounts(_ shelving: [BiblioShelving]) -> [String: Int] {
    var out: [String: Int] = [:]
    for entry in shelving {
        guard let rkey = AtUri(string: entry.value.shelf)?.rkey else { continue }
        out[rkey, default: 0] += 1
    }
    return out
}

/// Status of the shelving record for one reviewed book, for the review tabs.
func statusByBookUri(_ shelving: [BiblioShelving]) -> [String: String] {
    var out: [String: String] = [:]
    for entry in shelving {
        guard let uri = entry.value.book?.atUri, let status = entry.value.metadata?.status else { continue }
        out[uri] = status
    }
    return out
}

extension BiblioClient {
    /// Looks up an actor's identity for each DID, at most `concurrency`
    /// requests in flight. Reviews only carry a DID, so a card cannot name its
    /// author without this.
    func actors(for dids: [String], concurrency: Int = 6) async -> [String: BiblioActor] {
        let unique = Array(Set(dids.filter { !$0.isEmpty })).sorted()
        guard !unique.isEmpty else { return [:] }
        var out: [String: BiblioActor] = [:]
        for chunk in unique.chunked(into: concurrency) {
            let found = await withTaskGroup(of: (String, BiblioActor?).self) { group in
                for did in chunk {
                    group.addTask { [self] in
                        (did, try? await getActor(actor: did))
                    }
                }
                var collected: [(String, BiblioActor?)] = []
                for await item in group { collected.append(item) }
                return collected
            }
            for (did, actor) in found {
                if let actor { out[did] = actor }
            }
        }
        return out
    }

    /// Title, author, and cover for each edition URI, at most `concurrency`
    /// requests in flight. Unresolvable editions are dropped.
    func summaries(for bookUris: [String], concurrency: Int = 6) async -> [String: BookSummary] {
        let unique = Array(Set(bookUris.filter { !$0.isEmpty })).sorted()
        guard !unique.isEmpty else { return [:] }
        var out: [String: BookSummary] = [:]
        for chunk in unique.chunked(into: concurrency) {
            let found = await withTaskGroup(of: (String, BookSummary?).self) { group in
                for uri in chunk {
                    group.addTask { [self] in
                        (uri, await summarise(bookUri: uri))
                    }
                }
                var collected: [(String, BookSummary?)] = []
                for await item in group { collected.append(item) }
                return collected
            }
            for (uri, summary) in found {
                if let summary { out[uri] = summary }
            }
        }
        return out
    }

    /// One edition as a `BookSummary`, or nil when the record cannot be read.
    func summarise(bookUri: String) async -> BookSummary? {
        guard let atUri = AtUri(string: bookUri) else { return nil }
        guard let response = try? await getEdition(atUri: bookUri) else { return nil }
        let book = response.value
        let cover: URL?
        if let coverImage = book.coverImageUrl, let url = URL(string: coverImage) {
            cover = url
        } else {
            cover = try? await getImageForBook(atUri: bookUri)
        }
        return BookSummary(
            uri: bookUri,
            title: book.title?.nilIfEmpty ?? atUri.rkey,
            author: book.authorLine,
            coverURL: cover
        )
    }
}
