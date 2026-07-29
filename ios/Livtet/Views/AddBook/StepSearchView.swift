import SwiftUI

struct StepSearchView: View {
    @ObservedObject var viewModel: AddBookWizardViewModel
    @Environment(\.dismiss) private var dismiss

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
                Text("Add Book").font(.livtetHeading(size: 16, weight: .semibold))
                Spacer()
                Color.clear.frame(width: 80, height: 1)
            }.padding(.horizontal, 16).padding(.vertical, 12)

            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundStyle(Color("textQuiet"))
                TextField("Search by title or ISBN...", text: Binding(
                    get: { viewModel.data.searchQuery },
                    set: { viewModel.updateSearchQuery($0) }
                )).textFieldStyle(.plain)
                if viewModel.isSearching { ProgressView().scaleEffect(0.8) }
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: LivtetRadius.l).fill(Color("surfaceHighlighted")))
            .padding(.horizontal, 16)

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let providerError = viewModel.providerError {
                        ProviderErrorCallout(error: providerError) { viewModel.providerError = nil }
                    }
                    if !viewModel.data.localDedupResults.isEmpty { localResultsSection }
                    if !viewModel.data.searchResults.isEmpty {
                        onlineResultsSection
                    } else if !viewModel.isSearching && viewModel.data.searchQuery.count >= 3 {
                        Text("No results found. The title entered will be used for manual entry.")
                            .font(.livtetBody(size: 13)).foregroundStyle(Color("textQuiet"))
                    }
                }
                .padding(.horizontal, 16).padding(.vertical, 12)
            }

            Divider()
            Button("Skip search and add manually") { viewModel.skipSearch() }
                .font(.livtetBody(size: 14, weight: .semibold))
                .tint(.brand).padding(.vertical, 12)
        }
        .background(Color("surfaceDefault").ignoresSafeArea())
    }

    private var localResultsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Already in your library").font(.livtetBody(size: 12, weight: .semibold)).foregroundStyle(Color("textQuiet"))
            ForEach(viewModel.data.localDedupResults.prefix(3), id: \.id) { work in
                Text(work.title).font(.livtetBody(size: 14))
                    .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: LivtetRadius.l).fill(Color("surfaceHighlighted")))
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
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(Color("surfaceHighlighted")))
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
                    .background(RoundedRectangle(cornerRadius: LivtetRadius.l).fill(Color("surfaceHighlighted")))
                }
                .buttonStyle(.plain)
            }
        }
    }
}
