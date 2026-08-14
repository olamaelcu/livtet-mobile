package net.olamaelcu.livtet.wizard

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Public entry point for the AddBook wizard. Renders a full-screen
 * modal bottom sheet that drives the 5-step linear flow:
 *
 *   1. Title and Cover (both required; search inline)
 *   2. Contributors (≥ 1 author)
 *   3. Genres (optional)
 *   4. Subjects (optional)
 *   5. Tags (optional; save disabled in Phase 1)
 *
 * Phase 1: the save flow is gated by [AddBookWizardViewModel.isSaveAvailable].
 * Once the Phase 2 core/ FFI work lands, the flag flips and the existing
 * createBookComplete / findOrCreate* / linkWork* chain becomes callable
 * from the Tags step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBookWizard(
    onDismiss: () -> Unit,
    viewModel: AddBookWizardViewModel = viewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val step by viewModel.currentStep.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxSize(),
    ) {
        when (step) {
            WizardStep.TITLE_AND_COVER -> Step1TitleAndCover(viewModel = viewModel, onDismiss = onDismiss)
            WizardStep.CONTRIBUTORS -> Step2Contributors(viewModel = viewModel, onBack = { viewModel.goToBack() })
            WizardStep.GENRES -> Step3aGenres(viewModel = viewModel, onBack = { viewModel.goToBack() })
            WizardStep.SUBJECTS -> Step3bSubjects(viewModel = viewModel, onBack = { viewModel.goToBack() })
            WizardStep.TAGS -> Step4Tags(viewModel = viewModel, onBack = { viewModel.goToBack() })
        }
    }
}
