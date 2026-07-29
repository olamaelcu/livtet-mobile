import Foundation

struct CoverSearchResult: Equatable, Identifiable {
    let id = UUID()
    let title: String
    let coverUrl: URL?
    let source: String
}
