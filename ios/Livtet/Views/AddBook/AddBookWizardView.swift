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
        case .titleAndCover: return "Add Book - Title and Cover"
        case .contributors: return "Add Book - Contributors"
        case .genres: return "Add Book - Genres"
        case .subjects: return "Add Book - Subjects"
        case .tags: return "Add Book - Tags"
        case .hub: return "Add Book - More options"
        case .description: return "Add Book - Description"
        case .cover: return "Add Book - Cover"
        case .isbn: return "Add Book - ISBN"
        case .publishedDate: return "Add Book - Published Date"
        case .language: return "Add Book - Language"
        case .format: return "Add Book - Format"
        case .publisher: return "Add Book - Publisher"
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
        case .titleAndCover:
            Step1TitleAndCoverView(viewModel: viewModel)
        case .contributors:
            Step2ContributorsView(viewModel: viewModel)
        case .genres:
            Step3aGenresView(viewModel: viewModel)
        case .subjects:
            Step3bSubjectsView(viewModel: viewModel)
        case .tags:
            Step4TagsView(viewModel: viewModel)
        case .hub:
            HubView(viewModel: viewModel)
        case .description:
            DescriptionDetailView(viewModel: viewModel)
        case .cover:
            CoverDetailView(viewModel: viewModel)
        case .isbn:
            IsbnDetailView(viewModel: viewModel)
        case .publishedDate:
            PublishedDateDetailView(viewModel: viewModel)
        case .language:
            LanguageDetailView(viewModel: viewModel)
        case .format:
            FormatDetailView(viewModel: viewModel)
        case .publisher:
            PublisherDetailView(viewModel: viewModel)
        }
    }
}

#if DEBUG
#Preview {
    AddBookWizardView()
}
#endif
