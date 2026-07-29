import Foundation
import os.log

struct ImageDownloader {
    private static let logger = Logger(subsystem: "net.olamaelcu.livtet", category: "ImageDownloader")

    func download(from url: URL) async throws -> URL {
        let coversDir = try coversDirectory()
        let filename = "\(UUID().uuidString).jpg"
        let destination = coversDir.appendingPathComponent(filename)

        let (data, response) = try await URLSession.shared.data(from: url)
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            throw ImageDownloadError.badResponse
        }
        try data.write(to: destination, options: .atomic)
        Self.logger.info("Downloaded cover from \(url.absoluteString, privacy: .public)")
        return destination
    }

    func save(imageData: Data, id: String) throws -> URL {
        let dir = try manualCoversDirectory()
        let filename = "\(id)-\(UUID().uuidString).jpg"
        let destination = dir.appendingPathComponent(filename)
        try imageData.write(to: destination, options: .atomic)
        Self.logger.info("Saved manual cover for \(id, privacy: .public)")
        return destination
    }

    private func coversDirectory() throws -> URL {
        let docs = try FileManager.default.url(
            for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: true
        )
        let covers = docs.appendingPathComponent("covers", isDirectory: true)
        try FileManager.default.createDirectory(at: covers, withIntermediateDirectories: true)
        return covers
    }

    private func manualCoversDirectory() throws -> URL {
        let appSupport = try FileManager.default.url(
            for: .applicationSupportDirectory, in: .userDomainMask,
            appropriateFor: nil, create: true
        )
        let dir = appSupport
            .appendingPathComponent("net.olamaelcu.livtet/covers/manual", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }
}

enum ImageDownloadError: LocalizedError {
    case badResponse
    var errorDescription: String? {
        switch self { case .badResponse: return "Image download failed" }
    }
}
