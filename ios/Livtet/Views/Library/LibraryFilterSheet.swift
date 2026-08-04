import LivtetKitFFI
import SwiftUI

/// Modal sheet that lets the user pick format / language / status
/// filter chips for the Library tab. Mirrors Android's
/// `SearchFilterPanel` (shown inline under the search field) but
/// presented as a sheet because the iOS Library drops the persistent
/// search field.
///
/// Status chips are present and toggle the view-model's published
/// `selectedStatusIds` set, but `LibraryViewModel.fetchBooks()` does
/// not forward them to the FFI. This preserves the affordance so a
/// future build that wires the status predicate will light up without
/// UI changes.
///
/// All chip toggles mutate `viewModel` directly; the view-model's
/// 150 ms Combine debounce coalesces rapid changes into a single
/// refetch.
struct LibraryFilterSheet: View {
    @ObservedObject var viewModel: LibraryViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("Sort by") {
                    Picker("Sort by", selection: $viewModel.sortOrder) {
                        Text("Newest first").tag(BookSearchSortOrder.descending)
                        Text("Oldest first").tag(BookSearchSortOrder.ascending)
                    }
                    .pickerStyle(.segmented)
                    .accessibilityLabel("Sort by")
                    .accessibilityHint("Select the sort order for the library")
                }

                if !viewModel.formats.isEmpty {
                    Section("Format") {
                        FilterChipRow(
                            allLabel: "All formats",
                            selectedIds: $viewModel.selectedFormatIds,
                            entries: viewModel.formats.map { ($0.id, $0.name) }
                        )
                        .accessibilityLabel("Format filter")
                        .accessibilityHint("Select formats to filter the library")
                    }
                }

                if !viewModel.languages.isEmpty {
                    Section("Language") {
                        FilterChipRow(
                            allLabel: "All languages",
                            selectedIds: $viewModel.selectedLanguageIds,
                            entries: viewModel.languages.map { ($0.id, languageLabel($0)) }
                        )
                        .accessibilityLabel("Language filter")
                        .accessibilityHint("Select languages to filter the library")
                    }
                }

                if !viewModel.statuses.isEmpty {
                    Section {
                        // Status chips are inert — see LibraryViewModel.
                        // Present so the affordance exists when the
                        // deferred status predicate lands.
                        FilterChipRow(
                            allLabel: "All statuses",
                            selectedIds: $viewModel.selectedStatusIds,
                            entries: viewModel.statuses.map { ($0.id, $0.name) }
                        )
                        .accessibilityLabel("Status filter")
                        .accessibilityHint("Status filtering is coming soon")
                    } header: {
                        HStack {
                            Text("Status")
                            Spacer()
                            Text("Coming soon")
                                .font(.livtetBody(size: 11))
                                .foregroundStyle(Color("textQuiet"))
                        }
                    }
                }
            }
            .navigationTitle("Filter library")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Reset") {
                        viewModel.selectedFormatIds = []
                        viewModel.selectedLanguageIds = []
                        viewModel.selectedStatusIds = []
                        viewModel.sortOrder = .descending
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                        .font(.livtetBody(size: 14, weight: .semibold))
                }
            }
        }
    }

    /// Render the language name with an optional flag emoji prefix.
    private func languageLabel(_ language: LanguageInfo) -> String {
        if let flag = language.flagEmoji, !flag.isEmpty {
            return "\(flag) \(language.name)"
        }
        return language.name
    }
}

/// A horizontally-scrolling row of selectable chips plus an "All" chip
/// that clears the selection. Shared between the format / language /
/// status sections of [LibraryFilterSheet].
///
/// `entries` is a list of `(DbId, displayName)` pairs so the row is
/// independent of the concrete `FormatInfo` / `LanguageInfo` /
/// `WorkStatusInfo` uniffi-generated record types.
private struct FilterChipRow: View {
    let allLabel: String
    @Binding var selectedIds: Set<DbId>
    let entries: [(DbId, String)]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                FilterChip(label: allLabel, isSelected: selectedIds.isEmpty) {
                    selectedIds = []
                }
                ForEach(Array(entries.enumerated()), id: \.offset) { _, entry in
                    let (id, name) = entry
                    FilterChip(label: name, isSelected: selectedIds.contains(id)) {
                        if selectedIds.contains(id) {
                            selectedIds.remove(id)
                        } else {
                            selectedIds.insert(id)
                        }
                    }
                }
            }
            .padding(.vertical, 4)
        }
    }
}

/// A single tappable chip.
private struct FilterChip: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.livtetBody(size: 13, weight: isSelected ? .semibold : .regular))
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .foregroundStyle(isSelected ? Color("surfaceDefault") : Color("textNormal"))
                .background(
                    RoundedRectangle(cornerRadius: LivtetRadius.l, style: .continuous)
                        .fill(isSelected ? Color.brand : Color("surfaceDefault"))
                )
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityHint(isSelected ? "Selected. Double tap to deselect" : "Double tap to select")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

#if DEBUG
#Preview {
    LibraryFilterSheet(
        viewModel: LibraryViewModel(
            bridge: PreviewLibraryBridge()
        )
    )
}

/// Stub bridge for the filter-sheet preview only. Returns empty
/// filter dimensions so the sheet shows just the sort picker.
private final class PreviewLibraryBridge: LibraryBridge {
    func listBooksWithFilters(
        limit: Int32,
        offset: Int32,
        order: BookSearchSortOrder,
        filters: BookListFilters
    ) throws -> [Book] { [] }
    func getDistinctFormats() throws -> [FormatInfo] { [] }
    func getDistinctLanguages() throws -> [LanguageInfo] { [] }
    func getDistinctWorkStatuses() throws -> [WorkStatusInfo] { [] }
    func getEmptyStateQuotation() -> EmptyMessage {
        EmptyMessage(
            text: "I have a duty to speak the truth as I see it and to share not just my triumphs, not just the things that felt good, but the pain.",
            author: "Audre Lorde",
            material: "Sister Outsider"
        )
    }
    func getEditionsWithCoversForWork(workId: DbId) throws -> [Edition] { [] }
    func setEditionManualCover(editionId: DbId, localPath: String) throws {}
}
#endif
