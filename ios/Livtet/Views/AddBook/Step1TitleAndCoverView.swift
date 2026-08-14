import PhotosUI
import SwiftUI

/// Step 1 of the guided Add Book wizard. The user enters the title
/// (required) and a cover (required) — sourced from one of three places:
///
/// 1. An online search result (pre-fills both title and cover)
/// 2. A manually typed/pasted URL
/// 3. The OS photo picker
/// 4. The camera (UIImagePickerController wrapper)
///
/// In Phase 1 the cover is stored as a URL / pending-local URI only;
/// the actual download + `setEditionCover` path is deferred until the
/// Phase 2 core/ FFI work lands and `isSaveAvailable` flips to `true`.
struct Step1TitleAndCoverView: View {
    @ObservedObject var viewModel: AddBookWizardViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var pickerItem: PhotosPickerItem?
    @State private var showCamera = false
    @State private var showCoverSourceMenu = false

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button {
                    dismiss()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                        Text("Library")
                    }
                    .foregroundStyle(Color("textNormal"))
                }
                Spacer()
                Text("Title & Cover").font(.livtetHeading(size: 16, weight: .semibold))
                Spacer()
                Color.clear.frame(width: 80, height: 1)
            }.padding(.horizontal, 16).padding(.vertical, 12)

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    searchField
                    if let providerError = viewModel.providerError {
                        ProviderErrorCallout(error: providerError) { viewModel.providerError = nil }
                    }
                    if !viewModel.data.localDedupResults.isEmpty { localResultsSection }
                    if !viewModel.data.searchResults.isEmpty {
                        onlineResultsSection
                    } else if !viewModel.isSearching && viewModel.data.searchQuery.count >= 3 {
                        Text("No results found. Enter the title and cover manually below.")
                            .font(.livtetBody(size: 13)).foregroundStyle(Color("textQuiet"))
                    }
                    titleField
                    coverSection
                }
                .padding(.horizontal, 16).padding(.vertical, 12)
            }

            Divider()
            Button {
                viewModel.continueFromTitleAndCover()
            } label: {
                Text("Continue: Contributors")
                    .font(.livtetBody(size: 16, weight: .semibold))
            }
            .tint(.brand).frame(maxWidth: .infinity).padding(.vertical, 12)
            .disabled(!viewModel.canContinueFromTitleAndCover)
        }
        .background(Color("surfaceDefault").ignoresSafeArea())
        .photosPicker(isPresented: Binding(
            get: { pickerItem == nil ? false : true },
            set: { if !$0 { pickerItem = nil } }
        ), selection: $pickerItem, matching: .images)
        .onChange(of: pickerItem) { _, newItem in
            handlePickerSelection(newItem)
        }
        .sheet(isPresented: $showCamera) {
            CameraPicker { url in
                showCamera = false
                if let url {
                    viewModel.data.cover = .remote(url)
                }
            }
        }
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(Color("textQuiet"))
            TextField("Search by title or ISBN…", text: Binding(
                get: { viewModel.data.searchQuery },
                set: { viewModel.updateSearchQuery($0) }
            )).textFieldStyle(.plain)
            if viewModel.isSearching { ProgressView().scaleEffect(0.8) }
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: LivtetRadius.l).fill(Color("surfaceDefault")))
    }

    @ViewBuilder
    private var titleField: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Title *").font(.livtetBody(size: 12, weight: .semibold)).foregroundStyle(Color("textQuiet"))
            TextField("Book title", text: $viewModel.data.title).textFieldStyle(.roundedBorder)
        }
    }

    @ViewBuilder
    private var coverSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Cover *").font(.livtetBody(size: 12, weight: .semibold)).foregroundStyle(Color("textQuiet"))
            HStack(alignment: .top, spacing: 12) {
                CoverImageView(url: viewModel.data.cover?.displayURL, width: 96, height: 132)
                VStack(alignment: .leading, spacing: 8) {
                    Menu {
                        PhotosPicker(selection: $pickerItem, matching: .images) {
                            Label("Choose from Photos", systemImage: "photo.on.rectangle")
                        }
                        Button {
                            showCamera = true
                        } label: {
                            Label("Take Photo", systemImage: "camera")
                        }
                        Button {
                            viewModel.data.cover = nil
                        } label: {
                            Label("Clear", systemImage: "xmark")
                        }
                    } label: {
                        HStack {
                            Image(systemName: "photo.badge.plus")
                            Text("Cover source")
                        }
                        .font(.livtetBody(size: 14, weight: .semibold))
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        .background(RoundedRectangle(cornerRadius: LivtetRadius.l).fill(Color("surfaceDefault")))
                    }
                    if case .remote(let url) = viewModel.data.cover {
                        Text(url.absoluteString)
                            .font(.livtetBody(size: 11)).foregroundStyle(Color("textQuiet"))
                            .lineLimit(2)
                    }
                }
                Spacer(minLength: 0)
            }
            TextField("Or paste an image URL", text: coverURLStringBinding)
                .textFieldStyle(.roundedBorder)
                .autocapitalization(.none).keyboardType(.URL)
                .font(.livtetBody(size: 13))
        }
    }

    private var coverURLStringBinding: Binding<String> {
        Binding(
            get: {
                if case .remote(let url) = viewModel.data.cover { return url.absoluteString }
                return ""
            },
            set: { newValue in
                let trimmed = newValue.trimmingCharacters(in: .whitespacesAndNewlines)
                if trimmed.isEmpty {
                    viewModel.data.cover = nil
                } else if let url = URL(string: trimmed) {
                    viewModel.data.cover = .remote(url)
                }
            }
        )
    }

    private var localResultsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Already in your library").font(.livtetBody(size: 12, weight: .semibold)).foregroundStyle(Color("textQuiet"))
            ForEach(viewModel.data.localDedupResults.prefix(3), id: \.id) { work in
                Text(work.title).font(.livtetBody(size: 14))
                    .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: LivtetRadius.l).fill(Color("surfaceDefault")))
            }
        }
    }

    private var onlineResultsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Text("Online results").font(.livtetBody(size: 12, weight: .semibold)).foregroundStyle(Color("textQuiet"))
                if viewModel.searchSource == .openLibrary {
                    Text("via OpenLibrary")
                        .font(.livtetBody(size: 10, weight: .semibold))
                        .foregroundStyle(Color("textQuiet"))
                        .padding(.horizontal, 8).padding(.vertical, 3)
                        .background(Capsule().fill(Color("surfaceDefault")))
                }
            }
            ForEach(viewModel.data.searchResults) { result in
                Button { viewModel.selectResult(result) } label: {
                    HStack(spacing: 12) {
                        CoverImageView(url: result.coverUrl, width: 48, height: 64)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(result.title).font(.livtetHeading(size: 14, weight: .semibold)).foregroundStyle(Color("textNormal")).lineLimit(2)
                            if !result.authors.isEmpty {
                                Text(result.authors.joined(separator: ", ")).font(.livtetBody(size: 12)).foregroundStyle(Color("textQuiet")).lineLimit(1)
                            }
                            if let year = result.year {
                                Text(verbatim: String(year)).font(.livtetBody(size: 11)).foregroundStyle(Color("textQuiet").opacity(0.7))
                            }
                        }
                        Spacer(minLength: 8)
                        Image(systemName: "chevron.right").foregroundStyle(Color("textQuiet").opacity(0.4))
                    }
                    .padding(12)
                    .background(RoundedRectangle(cornerRadius: LivtetRadius.l).fill(Color("surfaceDefault")))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func handlePickerSelection(_ item: PhotosPickerItem?) {
        guard let item else { return }
        Task {
            if let data = try? await item.loadTransferable(type: Data.self) {
                let mime = item.supportedContentTypes.first?.preferredMIMEType ?? "image/jpeg"
                await MainActor.run {
                    viewModel.data.cover = .pendingLocal(
                        uri: "photos-picker://\(UUID().uuidString)",
                        mimeType: mime,
                        byteSize: data.count
                    )
                }
            }
        }
    }
}

/// Thin UIImagePickerController wrapper for the camera capture path.
/// In Phase 1 this only stores the captured image's URL — the actual
/// write-to-files-dir step is deferred until Phase 2's FFI lands.
private struct CameraPicker: UIViewControllerRepresentable {
    let onComplete: (URL?) -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onComplete: onComplete) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onComplete: (URL?) -> Void
        init(onComplete: @escaping (URL?) -> Void) { self.onComplete = onComplete }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            let url = info[.imageURL] as? URL
            picker.dismiss(animated: true)
            onComplete(url)
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            picker.dismiss(animated: true)
            onComplete(nil)
        }
    }
}
