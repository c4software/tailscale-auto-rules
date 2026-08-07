package fr.vbrosseau.tailscaleautorules.presentation.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.tailscaleautorules.domain.model.JournalEntry
import fr.vbrosseau.tailscaleautorules.domain.repository.JournalRepository
import fr.vbrosseau.tailscaleautorules.presentation.UiStateSharing
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** État de l'écran du journal. */
data class JournalUiState(
    val entries: List<JournalEntry> = emptyList(),
    val isLoading: Boolean = false,
) {
    val isEmpty: Boolean get() = entries.isEmpty()
}

/**
 * Alimente l'écran du journal.
 *
 * L'ordre — du plus récent au plus ancien — et la capacité sont garantis par le
 * repository : les refaire ici les dédoublerait, et les deux pourraient
 * diverger.
 */
@HiltViewModel
class JournalViewModel @Inject constructor(
    private val repository: JournalRepository,
) : ViewModel() {

    val uiState: StateFlow<JournalUiState> = repository.observeRecent()
        .map { entries -> JournalUiState(entries = entries) }
        .stateIn(viewModelScope, UiStateSharing, JournalUiState(isLoading = true))

    fun clear() {
        viewModelScope.launch { repository.clear() }
    }
}
