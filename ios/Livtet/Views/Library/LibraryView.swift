import Inject
import LivtetKit
import os
import SwiftUI

/// Details view for a single work — shows its metadata and the editions that
/// belong to it.
struct WorkDetailView: View {
    let work: WorkSummary

    @State private var editions: [EditionSummary] = []
    @State private var loadError: String?

    private let libraryBridge: LibraryBridge

    init(work: WorkSummary, libraryBridge: LibraryBridge = LivtetLibraryBridgeAdapter()) {
        self.work = work
        self.libraryBridge = libraryBridge
    }

    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    Text(work.title)
                        .font(.livtetHeading(size: 24, weight: .semibold))
                        .foregroundStyle(Color("textNormal"))

                    if !work.authors.isEmpty {
                        Text(work.authors.joined(separator: ", "))
                            .font(.livtetBody(size: 14))
                            .foregroundStyle(Color("textQuiet"))
                    }

                    if let description = work.description.nilIfEmpty {
                        Text(description)
                            .font(.livtetBody(size: 14))
                            .foregroundStyle(Color("textQuiet"))
                    }
                }
                .padding(.vertical, 8)
            }

            Section("Editions") {
                if let loadError {
                    Text(loadError)
                        .font(.livtetBody(size: 13))
                        .foregroundStyle(Color("textQuiet"))
                } else if editions.isEmpty {
                    Text("No editions")
                        .font(.livtetBody(size: 13))
                        .foregroundStyle(Color("textQuiet"))
                } else {
                    ForEach(editions, id: \.id) { edition in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(edition.title.nilIfEmpty ?? "Untitled Edition")
                                .font(.livtetBody(size: 14, weight: .medium))
                                .foregroundStyle(Color("textNormal"))

                            HStack(spacing: 8) {
                                if let format = edition.format, !format.isEmpty {
                                    Text(format)
                                }
                                if let language = edition.languageCode, !language.isEmpty {
                                    Text(language.uppercased())
                                }
                                if edition.hasFile {
                                    Label("File", systemImage: "doc")
                                }
                            }
                            .font(.livtetBody(size: 12))
                            .foregroundStyle(Color("textQuiet"))
                        }
                        .padding(.vertical, 2)
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .background(Color("surfaceDefault").ignoresSafeArea())
        .navigationTitle(work.title)
        .navigationBarTitleDisplayMode(.inline)
        .task { await loadEditions() }
    }

    private func loadEditions() async {
        do {
            editions = try await libraryBridge.getEditionsForWork(workId: work.id)
            loadError = nil
        } catch {
            loadError = error.localizedDescription
        }
    }
}

/// The Library tab of the Livtet iOS app.
///
/// Shows works flat — each work is a row. Editions are shown per work on the
/// detail screen.
struct LibraryView: View {
    @StateObject private var viewModel = LibraryViewModel()
    @State private var showFilterSheet = false
    @State private var showAddBookWizard = false
    @State private var selectedWork: WorkSummary?
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
                .navigationDestination(item: $selectedWork) { work in
                    WorkDetailView(work: work)
                }
        }
        .enableInjection()
    }

    @ViewBuilder
    private var content: some View {
        if viewModel.works.isEmpty {
            emptyOrErrorState
        } else if showGrid {
            workGrid
        } else {
            workList
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

    private var workList: some View {
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

            ForEach(viewModel.works, id: \.id) { work in
                WorkRow(work: work)
                    .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
                    .contentShape(Rectangle())
                    .onTapGesture {
                        Logger(subsystem: "net.olamaelcu.livtet", category: "LibraryView")
                            .debug("Work tapped: \(work.title, privacy: .public)")
                        selectedWork = work
                    }
            }
        }
        .listStyle(.plain)
    }

    private var workGrid: some View {
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
                    ForEach(viewModel.works, id: \.id) { work in
                        VStack(spacing: 8) {
                            Color("surfaceDefault")
                                .frame(width: maxCoverWidth, height: coverHeight)
                                .overlay {
                                    Image(systemName: "book.closed")
                                        .font(.livtetHeading(size: maxCoverWidth * 0.35))
                                        .foregroundStyle(Color("textQuiet").opacity(0.4))
                                }
                                .clipShape(RoundedRectangle(cornerRadius: LivtetRadius.m, style: .continuous))

                            Text(work.title)
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
                                .debug("Work tapped: \(work.title, privacy: .public)")
                            selectedWork = work
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
                    Text("Newest first").tag(SortDirection.desc)
                    Text("Oldest first").tag(SortDirection.asc)
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

            Button {
                showAddBookWizard = true
            } label: {
                Label("Add", systemImage: "plus")
            }
            .accessibilityLabel("Add book")
        }
    }
}

private extension Optional where Wrapped == String {
    var nilIfEmpty: String? {
        guard let self, !self.isEmpty else { return nil }
        return self
    }
}

#if DEBUG
#Preview {
    LibraryView()
}
#endif
