import Foundation

struct OpenLibraryResponse: Decodable {
    let numFound: Int
    let docs: [OpenLibraryDoc]
}

struct OpenLibraryDoc: Decodable, Equatable {
    let title: String?
    let authorName: [String]?
    let publisher: [String]?
    let firstPublishYear: Int?
    let isbn: [String]?
    let coverI: Int?
    let key: String?

    enum CodingKeys: String, CodingKey {
        case title
        case authorName = "author_name"
        case publisher
        case firstPublishYear = "first_publish_year"
        case isbn
        case coverI = "cover_i"
        case key
    }
}
