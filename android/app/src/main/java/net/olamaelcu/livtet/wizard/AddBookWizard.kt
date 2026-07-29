package net.olamaelcu.livtet.wizard

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBookWizard(onDismiss: () -> Unit) {
    var data by remember { mutableStateOf(WizardData()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxSize(),
    ) {
        when (data.currentStep) {
            0 -> StepSearch(data = data, onNext = { data = it }, onDismiss = onDismiss)
            1 ->
                StepAuthors(
                    data = data,
                    onBack = { data = data.copy(currentStep = 0) },
                    onNext = { data = it },
                )
            2 ->
                StepReview(
                    data = data,
                    onBack = { data = data.copy(currentStep = 1) },
                    onComplete = { onDismiss() },
                )
        }
    }
}
