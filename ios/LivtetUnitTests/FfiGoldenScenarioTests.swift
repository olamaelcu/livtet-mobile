import XCTest

// NOTE: imports the UniFFI module generated from core/livtet-ffi.
// The iOS Swift bindings for the revived livtet-ffi crate have not been
// wired into the Xcode project yet (regeneration happens on macOS via
// `mise run ios-bindings`); until then this file intentionally fails to
// build on macOS too and the task below stays red.
// TODO(ffi): after regenerating bindings, adjust the import module name
// if it differs and remove this note.
import LivtetFfi

/// Golden scenario across the UniFFI boundary (mirrors the Android
/// instrumented test in `android/app/src/androidTest/.../FfiGoldenScenarioTest.kt`).
final class FfiGoldenScenarioTests: XCTestCase {

    private var store: LivtetStore?

    override func tearDown() async throws {
        try? await store?.shutdown()
        store = nil
    }

    private func openFresh() async throws -> LivtetStore {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("ffi-golden-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return try await LivtetStore.open(
            dbPath: dir.appendingPathComponent("livtet.db").path,
            indexDir: dir.appendingPathComponent("search-index").path
        )
    }

    func testGoldenScenario() async throws {
        let store = try await openFresh()
        self.store = store

        // 1. Reset + seed.
        let seeded = try await store.resetAndSeed(numWorks: 5)
        XCTAssertEqual(seeded.worksCreated, 5)

        // 2. Work listing + newest-first ordering.
        let works = try await store.listWorks(
            limit: 100, offset: 0, sortBy: nil, sortDirection: nil)
        XCTAssertEqual(works.count, 5)
        let newest = try await store.listWorks(
            limit: 100, offset: 0, sortBy: .newestCap, sortDirection: nil)
        XCTAssertEqual(Set(newest.map(\.id)), Set(works.map(\.id)))

        // 3. Edition listing + detail round-trip.
        let workId = works[0].id
        let editions = try await store.listEditions(workId: workId)
        XCTAssertFalse(editions.isEmpty)
        let editionId = editions[0].id
        let detail = try await store.getEdition(id: editionId)
        XCTAssertEqual(detail?.id, editionId)
        XCTAssertEqual(detail?.workId, workId)

        // 4. Attach and detach a digital file.
        try await store.setEditionFile(
            editionId: editionId,
            filePath: "/tmp/golden.epub",
            fileFormat: "epub",
            fileSizeBytes: 1234
        )
        XCTAssertNotNil(try await store.getEdition(id: editionId)?.file)
        XCTAssertTrue(try await store.removeEditionFile(editionId: editionId))
        XCTAssertNil(try await store.getEdition(id: editionId)?.file)

        // 5. Identifier attachment is idempotent.
        _ = try await store.addEditionIdentifier(
            editionId: editionId, identifier: "urn:isbn:9780306406157")
        _ = try await store.addEditionIdentifier(
            editionId: editionId, identifier: "urn:isbn:9780306406157")
        let identifiers = try await store.getEdition(id: editionId)!.identifiers
        XCTAssertEqual(identifiers.filter { $0 == "urn:isbn:9780306406157" }.count, 1)

        // 6. Patch: title set works; partial published_date fails loud.
        let patched = try await store.updateEdition(
            editionId: editionId,
            patch: EditionPatch(
                title: "Golden Renamed",
                description: nil,
                notes: nil,
                publishedDate: nil,
                formatId: nil,
                languageId: nil
            )
        )
        XCTAssertEqual(patched.title, "Golden Renamed")
        XCTAssertThrowsError(
            try await store.updateEdition(
                editionId: editionId,
                patch: EditionPatch(
                    title: nil,
                    description: nil,
                    notes: nil,
                    publishedDate: .year(1989),
                    formatId: nil,
                    languageId: nil
                )
            )
        ) { error in
            guard case LivtetError.InvalidInput = error else {
                return XCTFail("expected LivtetError.invalidInput, got \(error)")
            }
        }

        // 7. Search: editions + collapsed works + facets.
        let editionHits = try await store.searchEditions(query: "", limit: 100, offset: 0)
        XCTAssertFalse(editionHits.isEmpty)
        let workHits = try await store.searchWorks(query: "", limit: 100, offset: 0)
        XCTAssertEqual(Set(workHits.map(\.workId)).count, 5)
        let faceted = try await store.searchWithFacets(query: "", limit: 100)
        XCTAssertFalse(faceted.hits.isEmpty)

        // 8. Work status round-trip.
        try await store.setWorkStatus(workId: workId, status: .reading)
        let status = try await store.getWorkStatus(workId: workId)
        XCTAssertEqual(status, .reading)

        // 9. Annotations CRUD.
        let annotation = try await store.addAnnotation(
            editionId: editionId, content: "Golden highlight", location: "ch1")
        let annotations = try await store.listAnnotations(editionId: editionId)
        XCTAssertTrue(annotations.contains { $0.id == annotation.id })
        XCTAssertTrue(try await store.deleteAnnotation(id: annotation.id))

        // 10. Reading lists.
        let list = try await store.createReadingList(name: "Golden", description: nil)
        try await store.addEditionToList(listId: list.id, editionId: editionId)
        let lists = try await store.listReadingLists()
        XCTAssertTrue(
            lists.first { $0.id == list.id }?.editionIds.contains(editionId) ?? false)
        XCTAssertTrue(try await store.removeEditionFromList(listId: list.id, editionId: editionId))
        XCTAssertTrue(try await store.deleteReadingList(listId: list.id))

        try await store.shutdown()
        self.store = nil
    }
}
