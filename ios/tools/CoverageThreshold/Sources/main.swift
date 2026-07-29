import Foundation
import XCResultKit

struct CoverageReport {
    let files: [CoverageFile]
    let overallCoverage: Double
    let totalCovered: Int
    let totalExecutable: Int
}

func loadCoverage(from path: String) throws -> CoverageReport {
    let url = URL(fileURLWithPath: path)
    guard FileManager.default.fileExists(atPath: path) else {
        throw CoverageError.fileNotFound(path)
    }

    let resultFile = XCResultFile(url: url)
    guard let coverage = resultFile.getCodeCoverage() else {
        throw CoverageError.noCoverageData
    }
    guard !coverage.isEmpty else {
        throw CoverageError.emptyCoverageData
    }

    let totalCovered = coverage.reduce(0) { $0 + $1.coveredLines }
    let totalExecutable = coverage.reduce(0) { $0 + $1.executableLines }
    let overallCoverage = totalExecutable > 0 ? Double(totalCovered) / Double(totalExecutable) : 0.0

    return CoverageReport(
        files: coverage,
        overallCoverage: overallCoverage,
        totalCovered: totalCovered,
        totalExecutable: totalExecutable
    )
}

func printReport(_ report: CoverageReport, threshold: Double) {
    print("=== Code Coverage Report ===")
    print(String(
        format: "Overall: %.1f%% (%d / %d executable lines)",
        report.overallCoverage * 100,
        report.totalCovered,
        report.totalExecutable
    ))
    print()

    let header = String(format: "%-50s %10s %10s %8s", "File", "Covered", "Total", "Coverage")
    print(header)
    print(String(repeating: "-", count: header.count))

    let sorted = report.files.sorted { $0.lineCoverage < $1.lineCoverage }
    for file in sorted {
        let name = (file.name as NSString).lastPathComponent
        let cov = file.lineCoverage * 100
        print(String(
            format: "%-50s %10d %10d %7.1f%%",
            name,
            file.coveredLines,
            file.executableLines,
            cov
        ))
    }

    print()
    print(String(format: "Threshold: %.1f%%", threshold * 100))

    if report.overallCoverage >= threshold {
        print("PASS: Coverage meets threshold.")
    } else {
        print(String(
            format: "FAIL: Coverage (%.1f%%) is below threshold (%.1f%%).",
            report.overallCoverage * 100,
            threshold * 100
        ))
    }
}

enum CoverageError: Error, CustomStringConvertible {
    case fileNotFound(String)
    case noCoverageData
    case emptyCoverageData
    case invalidThreshold(String)

    var description: String {
        switch self {
        case .fileNotFound(let path):
            return ".xcresult bundle not found at \(path)"
        case .noCoverageData:
            return "No coverage data found in .xcresult bundle"
        case .emptyCoverageData:
            return "Coverage data is empty"
        case .invalidThreshold(let raw):
            return "Invalid threshold value: \(raw)"
        }
    }
}

func parseThreshold(_ raw: String) throws -> Double {
    guard let value = Double(raw) else {
        throw CoverageError.invalidThreshold(raw)
    }
    return value / 100.0
}

func main() {
    let args = CommandLine.arguments

    guard args.count >= 2 else {
        print("Usage: CoverageThresholdChecker <path-to-xcresult> [threshold%]")
        exit(1)
    }

    let threshold: Double
    if args.count >= 3 {
        do {
            threshold = try parseThreshold(args[2])
        } catch {
            print("ERROR: \(error)")
            exit(1)
        }
    } else {
        threshold = 0.10
    }

    let report: CoverageReport
    do {
        report = try loadCoverage(from: args[1])
    } catch {
        print("ERROR: \(error)")
        exit(1)
    }

    printReport(report, threshold: threshold)
    exit(report.overallCoverage >= threshold ? 0 : 1)
}

main()
