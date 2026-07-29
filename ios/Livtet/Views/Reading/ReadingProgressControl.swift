import SwiftUI
import LivtetKit
import LivtetKitFFI

/// A format-adaptive progress input control for reading progress.
///
/// Displays appropriate UI based on format type:
/// - PhysicalBook: Numeric page spinner (1 to page_count)
/// - Ebook: Percentage slider (0-100%)
/// - Audiobook: Time picker (HH:MM:SS)
struct ReadingProgressControl: View {
    @Binding var progress: Double
    let formatSchema: FormatSchema?
    let totalReadingTimeSecs: Int64

    @State private var pageText: String = ""
    @State private var hours: Int = 0
    @State private var minutes: Int = 0
    @State private var seconds: Int = 0

    private var progressUnit: ProgressUnit {
        formatSchema?.progressUnit ?? .ratio
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Reading Progress")
                .font(.headline)

            switch progressUnit {
            case .page:
                pageInputView
            case .ratio:
                ratioSliderView
            case .seconds:
                timeInputView
            }

            // Display current progress
            Text(displayText)
                .font(.caption)
                .foregroundStyle(.secondary)

            // Show total reading time
            if totalReadingTimeSecs > 0 {
                Text("Total reading time: \(formatReadingTime(totalReadingTimeSecs))")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }
        }
        .padding()
        .onAppear {
            initializeState()
        }
    }

    // MARK: - Physical Book (Page Input)

    private var pageInputView: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                TextField("Page", text: $pageText)
                    .textFieldStyle(.roundedBorder)
                    .keyboardType(.numberPad)
                    .onSubmit {
                        updateProgressFromPage()
                    }

                Button("Set") {
                    updateProgressFromPage()
                }
                .buttonStyle(.borderedProminent)
            }

            if let pageCount = formatSchema?.pageCount {
                Text("of \(pageCount) pages")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func updateProgressFromPage() {
        guard let page = Int(pageText), page >= 1 else { return }

        if let pageCount = formatSchema?.pageCount, page > pageCount {
            // Clamp to page count
            progress = Double(pageCount)
        } else {
            progress = Double(page)
        }

        // Convert to ratio for storage
        if let pageCount = formatSchema?.pageCount, pageCount > 0 {
            progress = progress / Double(pageCount)
        }
    }

    // MARK: - Ebook (Ratio Slider)

    private var ratioSliderView: some View {
        VStack(alignment: .leading, spacing: 8) {
            Slider(value: $progress, in: 0...1) {
                Text("Progress")
            } minimumValueLabel: {
                Text("0%")
            } maximumValueLabel: {
                Text("100%")
            }

            HStack {
                Spacer()
                Text("\(Int(progress * 100))%")
                    .font(.title2)
                    .fontWeight(.semibold)
                Spacer()
            }
        }
    }

    // MARK: - Audiobook (Time Input)

    private var timeInputView: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                // Hours
                VStack {
                    Text("Hours")
                        .font(.caption2)
                    Stepper("\(hours)", value: $hours, in: 0...23)
                        .labelsHidden()
                }

                Text(":")

                // Minutes
                VStack {
                    Text("Minutes")
                        .font(.caption2)
                    Stepper("\(minutes)", value: $minutes, in: 0...59)
                        .labelsHidden()
                }

                Text(":")

                // Seconds
                VStack {
                    Text("Seconds")
                        .font(.caption2)
                    Stepper("\(seconds)", value: $seconds, in: 0...59)
                        .labelsHidden()
                }
            }
            .onChange(of: hours) { _, _ in updateProgressFromTime() }
            .onChange(of: minutes) { _, _ in updateProgressFromTime() }
            .onChange(of: seconds) { _, _ in updateProgressFromTime() }

            if let duration = formatSchema?.durationSeconds {
                Text("Total: \(formatSeconds(duration))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func updateProgressFromTime() {
        let totalSeconds = hours * 3600 + minutes * 60 + seconds

        if let duration = formatSchema?.durationSeconds, duration > 0 {
            // Clamp to duration
            let clamped = min(totalSeconds, duration)
            progress = Double(clamped) / Double(duration)
        } else {
            progress = Double(totalSeconds)
        }
    }

    // MARK: - Helpers

    private var displayText: String {
        switch progressUnit {
        case .page:
            let currentPage = Int(progress)
            if let pageCount = formatSchema?.pageCount {
                return "Page \(currentPage) of \(pageCount)"
            } else {
                return "Page \(currentPage)"
            }
        case .ratio:
            return "\(Int(progress * 100))% complete"
        case .seconds:
            let totalSeconds = Int(progress)
            return formatSeconds(totalSeconds)
        }
    }

    private func formatSeconds(_ seconds: Int) -> String {
        let hours = seconds / 3600
        let minutes = (seconds % 3600) / 60
        let secs = seconds % 60

        if hours > 0 {
            return "\(hours)h \(minutes)m"
        } else if minutes > 0 {
            return "\(minutes)m \(secs)s"
        } else {
            return "\(secs)s"
        }
    }

    private func formatReadingTime(_ seconds: Int64) -> String {
        let hours = seconds / 3600
        let minutes = (seconds % 3600) / 60

        if hours > 0 {
            return "\(hours)h \(minutes)m"
        } else {
            return "\(minutes)m"
        }
    }

    private func initializeState() {
        switch progressUnit {
        case .page:
            pageText = "\(Int(progress))"
        case .ratio:
            break // Slider handles it
        case .seconds:
            let totalSeconds = Int(progress)
            hours = totalSeconds / 3600
            minutes = (totalSeconds % 3600) / 60
            seconds = totalSeconds % 60
        }
    }
}