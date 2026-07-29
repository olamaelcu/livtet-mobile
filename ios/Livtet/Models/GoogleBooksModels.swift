import Foundation

struct GoogleBooksResponse: Decodable {
    let totalItems: Int?
    let items: [GoogleBooksItem]?
}

struct GoogleBooksItem: Decodable, Equatable {
    let id: String
    let volumeInfo: VolumeInfo
}

struct VolumeInfo: Decodable, Equatable {
    let title: String
    let authors: [String]?
    let publisher: String?
    let publishedDate: String?
    let description: String?
    let industryIdentifiers: [IndustryIdentifier]?
    let pageCount: Int?
    let categories: [String]?
    let imageLinks: ImageLinks?
    let language: String?
}

struct IndustryIdentifier: Decodable, Equatable {
    let type: String
    let identifier: String
}

struct ImageLinks: Decodable, Equatable {
    let thumbnail: String?
    let smallThumbnail: String?
}
