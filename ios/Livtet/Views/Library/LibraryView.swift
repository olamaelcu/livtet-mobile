import Inject
import LivtetKit
import LivtetKitFFI
import os
import SwiftUI

/// Details view for a single edition — shows cover, metadata, and a
/// button to open a cover source sheet.
struct EditionDetailView: View {
    @State var edition: Edition

    @State private var showCoverSheet = false

    private let libraryBridge: LibraryBridge

    init(edition: Edition, libraryBridge: LibraryBridge = LivtetLibraryBridgeAdapter()) {
        self.edition = edition
        self.libraryBridge = libraryBridge
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if let coverPath = edition.coverPath, let url = URL(string: "file://\(coverPath)") {
                    CoverImageView(url: url, width: 200, height: 300)
                        .accessibilityHidden(true)
                } else {
                    Color("surfaceHighlighted")
                        .frame(width: 200, height: 300)
                        .overlay {
                            Image(systemName: "book.closed")
                                .font(.livtetHeading(size: 60))
                                .foregroundStyle(Color("textQuiet").opacity(0.6))
                        }
                }

                if let source = edition.coverSource {
                    Text("Cover source: \(source)")
                        .font(.livtetBody(size: 12))
                        .foregroundStyle(Color("textQuiet"))
                }

                Text(edition.editionTitle.nilIfEmpty ?? "Untitled Edition")
                    .font(.livtetHeading(size: 24, weight: .semibold))
                    .foregroundStyle(Color("textNormal"))

                VStack(alignment: .leading, spacing: 8) {
                    if let isbn = edition.isbn {
                        Text("ISBN: \(isbn)")
                            .font(.livtetBody(size: 14))
                            .foregroundStyle(Color("textQuiet"))
                    }

                    if let publishedDate = edition.publishedDate {
                        Text("Published: \(publishedDate)")
                            .font(.livtetBody(size: 14))
                            .foregroundStyle(Color("textQuiet"))
                    }

                    if let pageCount = edition.pageCount {
                        Text("\(pageCount) pages")
                            .font(.livtetBody(size: 14))
                            .foregroundStyle(Color("textQuiet"))
                    }
                }

                if let notes = edition.notes, !notes.isEmpty {
                    Text(notes)
                        .font(.livtetBody(size: 14))
                        .foregroundStyle(Color("textQuiet"))
                }

                if let description = edition.description, !description.isEmpty {
                    Text(description)
                        .font(.livtetBody(size: 16))
                        .foregroundStyle(Color("textQuiet"))
                }

                Button("Set Cover") {
                    showCoverSheet = true
                }
                .buttonStyle(.borderedProminent)
                .tint(.brand)
            }
            .padding(16)
        }
        .background(Color("surfaceDefault").ignoresSafeArea())
        .navigationTitle(edition.editionTitle.nilIfEmpty ?? "Edition")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showCoverSheet) {
            SetCoverSheet(
                edition: edition,
                libraryBridge: libraryBridge,
                wizardBridge: LivtetWizardBridgeAdapter()
            ) {
                refreshEdition()
            }
        }
    }

    private func refreshEdition() {
        Task {
            do {
                let refreshed = try libraryBridge.getEditionsWithCoversForWork(workId: edition.workId)
                if let updated = refreshed.first(where: { $0.id == edition.id }) {
                    edition = updated
                }
            } catch {}
        }
    }
}

private extension Optional where Wrapped == String {
    var nilIfEmpty: String? {
        guard let self, !self.isEmpty else { return nil }
        return self
    }
}

/// The Library tab of the Livtet iOS app.
///
/// Shows editions flat — each edition is a row. No work grouping.
struct LibraryView: View {
    @StateObject private var viewModel = LibraryViewModel()
    @State private var showFilterSheet = false
    @State private var showAddBookWizard = false
    @State private var selectedEdition: Edition?
    @State private var showGrid = false

    @ObserveInjection var forceRedraw

    var body: some View {
        NavigationStack {
            content
                .background(Color("surfaceDefault").ignoresSafeArea())
                .navigationTitle("Library")
                .toolbar { toolbarContent }
                .refreshable { viewModel.refresh() }
                .task { viewModel.load() }
                .sheet(isPresented: $showFilterSheet) {
                    LibraryFilterSheet(viewModel: viewModel)
                        .presentationDetents([.medium, .large])
                }
                .fullScreenCover(isPresented: $showAddBookWizard) {
                    AddBookWizardView()
                }
                .navigationDestination(item: $selectedEdition) { edition in
                    EditionDetailView(edition: edition)
                }
        }
        .enableInjection()
    }

    @ViewBuilder
    private var content: some View {
        if viewModel.editions.isEmpty {
            emptyOrErrorState
        } else if showGrid {
            editionGrid
        } else {
            editionList
        }
    }

    @ViewBuilder
    private var emptyOrErrorState: some View {
        ScrollView {
            VStack(spacing: 20) {
                if let error = viewModel.error {
                    ErrorBanner(
                        message: error.localizedDescription,
                        onRetry: { viewModel.retry() }
                    )
                    Text("Add your first book to get started.")
                        .font(.livtetBody(size: 14))
                        .foregroundStyle(Color("textNormal"))
                    addBookCTA
                } else if !viewModel.isLoading {
                    Image(systemName: "books.vertical")
                        .font(.livtetHeading(size: 48))
                        .foregroundStyle(Color("textQuiet").opacity(0.6))
                        .accessibilityHidden(true)
                    Text("No books yet")
                        .font(.livtetHeading(size: 22, weight: .semibold))
                        .foregroundStyle(Color("textNormal"))
                        .accessibilityLabel("No books yet")
                    Text("Add your first book to get started.")
                        .font(.livtetBody(size: 14))
                        .foregroundStyle(Color("textNormal"))
                        .accessibilityLabel("Add your first book to get started")
                    addBookCTA
                    if let message = viewModel.emptyMessage {
                        EmptyStateQuoteView(message: message)
                            .padding(.top, 4)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .padding(.horizontal, 24)
            .padding(.vertical, 24)
        }
        .background(Color("surfaceDefault"))
    }

    private var addBookCTA: some View {
        Button {
            showAddBookWizard = true
        } label: {
            Text("Add Book")
                .font(.livtetBody(size: 14, weight: .semibold))
        }
        .buttonStyle(.borderedProminent)
        .tint(.brand)
    }

    private var editionList: some View {
        List {
            if let error = viewModel.error {
                ErrorBanner(
                    message: error.localizedDescription,
                    onRetry: { viewModel.retry() }
                )
                .listRowInsets(EdgeInsets())
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)
            }

            ForEach(viewModel.editions, id: \.id) { edition in
                EditionRow(edition: edition)
                    .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
                    .contentShape(Rectangle())
                    .onTapGesture {
                        Logger(subsystem: "net.olamaelcu.livtet", category: "LibraryView")
                            .debug("Edition tapped: \(edition.workTitle ?? edition.editionTitle ?? "Unknown", privacy: .public)")
                        selectedEdition = edition
                    }
            }
        }
        .listStyle(.plain)
    }

    private var editionGrid: some View {
        GeometryReader { geo in
            let screenWidth = geo.size.width
            let padPct = screenWidth * 0.015
            let maxCoverWidth = screenWidth * 0.31
            let coverHeight = maxCoverWidth * 1.5

            let columns = [
                GridItem(.flexible(), spacing: 0),
                GridItem(.flexible(), spacing: 0),
                GridItem(.flexible(), spacing: 0),
            ]
            ScrollView {
                LazyVGrid(columns: columns, spacing: 0) {
                    ForEach(viewModel.editions, id: \.id) { edition in
                        VStack(spacing: 8) {
                            if let coverPath = edition.coverPath,
                               let url = URL(string: "file://\(coverPath)") {
                                CoverImageView(url: url, width: maxCoverWidth, height: coverHeight)
                                    .clipShape(RoundedRectangle(cornerRadius: LivtetRadius.m, style: .continuous))
                            } else {
                                Color("surfaceHighlighted")
                                    .frame(width: maxCoverWidth, height: coverHeight)
                                    .overlay {
                                        Image(systemName: "book.closed")
                                            .font(.livtetHeading(size: maxCoverWidth * 0.35))
                                            .foregroundStyle(Color("textQuiet").opacity(0.4))
                                    }
                                    .clipShape(RoundedRectangle(cornerRadius: LivtetRadius.m, style: .continuous))
                            }

                            Text(edition.workTitle.nilIfEmpty ?? edition.editionTitle.nilIfEmpty ?? "")
                                .font(.livtetBody(size: 11, weight: .medium))
                                .foregroundStyle(Color("textNormal"))
                                .lineLimit(2)
                                .multilineTextAlignment(.center)
                                .frame(maxWidth: maxCoverWidth)
                        }
                        .padding(padPct)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            Logger(subsystem: "net.olamaelcu.livtet", category: "LibraryView")
                                .debug("Edition tapped: \(edition.workTitle ?? edition.editionTitle ?? "Unknown", privacy: .public)")
                            selectedEdition = edition
                        }
                    }
                }
            }
        }
    }

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .navigationBarLeading) {
            Button {
                showGrid.toggle()
            } label: {
                Label("Toggle view", systemImage: showGrid ? "list.bullet" : "square.grid.3x3")
            }
            .accessibilityLabel(showGrid ? "Show list" : "Show grid")
        }

        ToolbarItemGroup(placement: .navigationBarTrailing) {
            Menu {
                Picker("Sort by", selection: $viewModel.sortOrder) {
                    Text("Newest first").tag(BookSearchSortOrder.descending)
                    Text("Oldest first").tag(BookSearchSortOrder.ascending)
                }
            } label: {
                Label("Sort", systemImage: "chevron.down")
            }

            Button {
                showFilterSheet = true
            } label: {
                Text("Filter")
            }
            .tint(.brand)
            .accessibilityLabel("Filter library")

            NavigationLink {
                DuplicateManagementView()
            } label: {
                Label("Duplicates", systemImage: "doc.on.doc")
            }
            .accessibilityLabel("Manage duplicates")

            Button {
                showAddBookWizard = true
            } label: {
                Label("Add", systemImage: "plus")
            }
            .accessibilityLabel("Add book")
        }
    }
}

#if DEBUG
#Preview {
    LibraryView()
}
#endif
