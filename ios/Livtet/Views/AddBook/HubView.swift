import SwiftUI

struct HubView: View {
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

            ScrollView {
                VStack(spacing: 24) {
                    if let warning = viewModel.partialSaveWarning {
                        ErrorBanner(message: warning, onRetry: {})
                    }
                    hubSection("Title & Cover") {
                        hubRow("Title",
                               status: viewModel.isItemFilled(.titleAndCover) ? .filled : .required,
                               preview: viewModel.previewForItem(.titleAndCover),
                               action: { viewModel.goToPage(.titleAndCover) })
                        hubRow("Contributors",
                               status: viewModel.isItemFilled(.contributors) ? .filled : .required,
                               preview: viewModel.previewForItem(.contributors),
                               action: { viewModel.goToPage(.contributors) })
                    }
                    hubSection("Details") {
                        VStack(spacing: 8) {
                            detailRow(.description)
                            detailRow(.isbn)
                            detailRow(.publishedDate)
                            detailRow(.language)
                            detailRow(.format)
                            detailRow(.publisher)
                        }
                    }
                    hubSection("Categories") {
                        VStack(spacing: 8) {
                            detailRow(.tags)
                            detailRow(.genres)
                            detailRow(.subjects)
                        }
                    }
                }
                .padding(.horizontal, 16).padding(.vertical, 16)
            }

            Divider()
            if viewModel.isSaveAvailable {
                Button("Create Book") { viewModel.save() }
                    .font(.livtetBody(size: 16, weight: .semibold))
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(Color.brand).foregroundStyle(Color("surfaceDefault"))
                    .clipShape(RoundedRectangle(cornerRadius: LivtetRadius.l))
                    .disabled(!viewModel.canCreateBook || viewModel.isSaving)
                    .padding(.horizontal, 16).padding(.vertical, 8)
                if viewModel.isSaving { ProgressView() }
            } else {
                Text("Saving is coming soon — the Hub is reachable from the Tags step's “More options” link.")
                    .font(.livtetBody(size: 12))
                    .foregroundStyle(Color("textQuiet"))
                    .padding(.horizontal, 16).padding(.vertical, 12)
            }
        }
        .background(Color("surfaceDefault").ignoresSafeArea())
    }

    private func detailRow(_ page: WizardPage) -> some View {
        hubRow(label(for: page),
               status: viewModel.isItemFilled(page) ? .filled : .empty,
               preview: viewModel.previewForItem(page),
               action: { viewModel.goToPage(page) })
    }

    private func label(for page: WizardPage) -> String {
        switch page {
        case .description: return "Description"
        case .isbn: return "ISBN"
        case .publishedDate: return "Published Date"
        case .language: return "Language"
        case .format: return "Format"
        case .publisher: return "Publisher"
        case .tags: return "Tags"
        case .genres: return "Genres"
        case .subjects: return "Subjects"
        default: return ""
        }
    }

    private func hubSection<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.livtetBody(size: 12, weight: .semibold)).foregroundStyle(Color("textQuiet")).padding(.horizontal, 4)
            content()
        }
    }

    private func hubRow(_ title: String, status: HubRowStatus, preview: String?, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: status.iconName).foregroundStyle(status.color)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.livtetBody(size: 14, weight: .medium)).foregroundStyle(Color("textNormal"))
                    if let preview, !preview.isEmpty {
                        Text(preview).font(.livtetBody(size: 12)).foregroundStyle(Color("textQuiet")).lineLimit(1)
                    }
                }
                Spacer()
                Image(systemName: "chevron.right").font(.livtetBody(size: 12)).foregroundStyle(Color("textQuiet").opacity(0.5))
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: LivtetRadius.l).fill(Color("surfaceDefault")))
        }
        .buttonStyle(.plain)
    }
}

private enum HubRowStatus {
    case filled, empty, required
    var iconName: String {
        switch self {
        case .filled: return "checkmark.circle.fill"
        case .empty: return "circle"
        case .required: return "exclamationmark.circle.fill"
        }
    }
    var color: Color {
        switch self {
        case .filled: return Color("successOnNormal")
        case .empty: return Color("textQuiet").opacity(0.4)
        case .required: return Color("dangerOnNormal")
        }
    }
}
