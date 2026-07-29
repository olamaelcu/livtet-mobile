import LivtetKitFFI
import PhotosUI
import SwiftUI

struct SetCoverSheet: View {
    @StateObject var viewModel: SetCoverViewModel

    @Environment(\.dismiss) private var dismiss

    init(
        edition: Edition,
        libraryBridge: LibraryBridge,
        wizardBridge: WizardBridge,
        googleBooksClient: GoogleBooksClient = GoogleBooksClient(),
        openLibraryClient: OpenLibraryClient = OpenLibraryClient(),
        onComplete: @escaping () -> Void
    ) {
        _viewModel = StateObject(wrappedValue: SetCoverViewModel(
            edition: edition,
            libraryBridge: libraryBridge,
            wizardBridge: wizardBridge,
            googleBooksClient: googleBooksClient,
            openLibraryClient: openLibraryClient,
            onComplete: {
                onComplete()
            }
        ))
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Picker("Source", selection: $viewModel.selectedTab) {
                    Text("Search").tag(0)
                    Text("URL").tag(1)
                    Text("Gallery").tag(2)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)

                switch viewModel.selectedTab {
                case 0: searchTab
                case 1: urlTab
                case 2: galleryTab
                default: EmptyView()
                }
            }
            .navigationTitle("Set Cover")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    // MARK: - Search Tab

    private var searchTab: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                TextField("Search for a cover...", text: $viewModel.searchQuery)
                    .textFieldStyle(.roundedBorder)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)
                    .onSubmit { viewModel.runSearch() }

                Button("Search") { viewModel.runSearch() }
                    .buttonStyle(.borderedProminent)
                    .tint(.brand)
                    .disabled(viewModel.searchQuery.trimmingCharacters(in: .whitespaces).count < 3)
            }

            if viewModel.searchQuery.trimmingCharacters(in: .whitespaces).count < 3 {
                Text("Enter at least 3 characters to search")
                    .font(.livtetBody(size: 12))
                    .foregroundStyle(Color("textQuiet"))
            }

            if viewModel.isSearching {
                ProgressView()
                    .frame(maxWidth: .infinity)
            }

            if let error = viewModel.searchError {
                Text(error)
                    .font(.livtetBody(size: 12))
                    .foregroundStyle(Color("semanticDangerForeground"))
            }

            if viewModel.searchSource == .openLibrary {
                HStack(spacing: 4) {
                    Text("via OpenLibrary")
                        .font(.livtetBody(size: 10, weight: .semibold))
                        .foregroundStyle(Color("textQuiet"))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(Color("surfaceHighlighted")))
                    Spacer()
                }
            }

            List(viewModel.searchResults) { result in
                Button {
                    Task {
                        await viewModel.applySearchResult(result)
                        dismiss()
                    }
                } label: {
                    HStack(spacing: 12) {
                        if let url = result.coverUrl {
                            CoverImageView(url: url, width: 40, height: 56)
                        } else {
                            Color("surfaceHighlighted")
                                .frame(width: 40, height: 56)
                                .clipShape(RoundedRectangle(cornerRadius: LivtetRadius.s, style: .continuous))
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(result.title)
                                .font(.livtetBody(size: 14, weight: .semibold))
                                .foregroundStyle(Color("textNormal"))
                                .lineLimit(2)
                            Text(result.source)
                                .font(.livtetBody(size: 11))
                                .foregroundStyle(Color("textQuiet"))
                        }
                        Spacer()
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            .listStyle(.plain)
        }
        .padding(.horizontal, 16)
    }

    // MARK: - URL Tab

    private var urlTab: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Paste a URL to a cover image.")
                .font(.livtetBody(size: 14))
                .foregroundStyle(Color("textQuiet"))

            HStack(spacing: 8) {
                TextField("https://example.com/cover.jpg", text: $viewModel.urlInput)
                    .textFieldStyle(.roundedBorder)
                    .keyboardType(.URL)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)

                Button("Set") {
                    Task {
                        await viewModel.applyUrl()
                        if viewModel.urlError == nil { dismiss() }
                    }
                }
                .buttonStyle(.borderedProminent)
                .tint(.brand)
                .disabled(viewModel.urlInput.isEmpty || viewModel.isDownloadingUrl)
            }

            if viewModel.isDownloadingUrl {
                ProgressView()
            }

            if let error = viewModel.urlError {
                Text(error)
                    .font(.livtetBody(size: 12))
                    .foregroundStyle(Color("semanticDangerForeground"))
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 12)
    }

    // MARK: - Gallery Tab

    private var galleryTab: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Choose a photo from your library.")
                .font(.livtetBody(size: 14))
                .foregroundStyle(Color("textQuiet"))

            PhotosPicker(
                selection: $viewModel.selectedPhoto,
                matching: .images,
                photoLibrary: .shared()
            ) {
                Label("Choose from Library", systemImage: "photo.on.rectangle")
                    .font(.livtetBody(size: 14, weight: .semibold))
            }
            .buttonStyle(.borderedProminent)
            .tint(.brand)
            .onChange(of: viewModel.selectedPhoto) { _, _ in
                Task { await viewModel.loadPhotoPreview() }
            }

            if let preview = viewModel.selectedPhotoPreview {
                HStack(spacing: 12) {
                    preview
                        .resizable()
                        .scaledToFill()
                        .frame(width: 120, height: 168)
                        .clipShape(RoundedRectangle(cornerRadius: LivtetRadius.l, style: .continuous))

                    VStack(alignment: .leading, spacing: 8) {
                        Button("Set Cover") {
                            Task {
                                await viewModel.applyPhotoItem()
                                if viewModel.galleryError == nil { dismiss() }
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.brand)
                        .disabled(viewModel.isSavingPhoto)
                    }
                }
            }

            if viewModel.isSavingPhoto {
                ProgressView()
            }

            if let error = viewModel.galleryError {
                Text(error)
                    .font(.livtetBody(size: 12))
                    .foregroundStyle(Color("semanticDangerForeground"))
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 12)
    }
}
