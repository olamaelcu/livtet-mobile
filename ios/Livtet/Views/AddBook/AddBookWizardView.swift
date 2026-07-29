import Inject
import SwiftUI

struct AddBookWizardView: View {
    @StateObject private var viewModel: AddBookWizardViewModel
    @Environment(\.dismiss) private var dismiss

    @ObserveInjection var forceRedraw

    @MainActor
    init() {
        _viewModel = StateObject(wrappedValue: AddBookWizardViewModel())
    }

    init(viewModel: AddBookWizardViewModel) {
        _viewModel = StateObject(wrappedValue: viewModel)
    }

    private var currentPageLabel: String {
        switch viewModel.currentPage {
        case .search: return "Add Book - Search"
        case .titleAndAuthors: return "Add Book - Title and Authors"
        case .hub: return "Add Book - Hub (complete or skip)"
        case .description: return "Add Book - Description"
        case .cover: return "Add Book - Cover"
        case .isbn: return "Add Book - ISBN"
        case .publishedDate: return "Add Book - Published Date"
        case .language: return "Add Book - Language"
        case .format: return "Add Book - Format"
        case .publisher: return "Add Book - Publisher"
        case .tags: return "Add Book - Tags"
        case .genres: return "Add Book - Genres"
        case .subjects: return "Add Book - Subjects"
        }
    }

    var body: some View {
        NavigationStack {
            content
                .navigationBarHidden(true)
                .accessibilityLabel(currentPageLabel)
        }
        .overlay {
            if viewModel.duplicateSummary != nil && !viewModel.isSaving {
                ZStack {
                    Color.black.opacity(0.4).ignoresSafeArea()
                    DuplicateWorkDialog(viewModel: viewModel)
                        .accessibilityLabel("Duplicate Found")
                        .accessibilityHint("A duplicate book was found. Choose how to handle it.")
                }
            }
        }
        .onChange(of: viewModel.didCompleteSave) { _, completed in
            if completed { dismiss() }
        }
        .enableInjection()
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.currentPage {
        case .search: StepSearchView(viewModel: viewModel)
        case .titleAndAuthors: StepTitleAndAuthorsView(viewModel: viewModel)
        case .hub: HubView(viewModel: viewModel)
        case .description: DescriptionDetailView(viewModel: viewModel)
        case .cover: CoverDetailView(viewModel: viewModel)
        case .isbn: IsbnDetailView(viewModel: viewModel)
        case .publishedDate: PublishedDateDetailView(viewModel: viewModel)
        case .language: LanguageDetailView(viewModel: viewModel)
        case .format: FormatDetailView(viewModel: viewModel)
        case .publisher: PublisherDetailView(viewModel: viewModel)
        case .tags: TagsDetailView(viewModel: viewModel)
        case .genres: GenresDetailView(viewModel: viewModel)
        case .subjects: SubjectsDetailView(viewModel: viewModel)
        }
    }
}

#if DEBUG
#Preview {
    AddBookWizardView()
}
#endif
