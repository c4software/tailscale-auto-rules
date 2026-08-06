package fr.vbrosseau.tailscaleautorules.presentation.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.tailscaleautorules.domain.model.JournalEntry
import fr.vbrosseau.tailscaleautorules.domain.repository.JournalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** État de l'écran du journal. */
data class JournalUiState(
    val entries: List<JournalEntry> = emptyList(),
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

    private val _uiState = MutableStateFlow(JournalUiState())
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeRecent().collect { entries ->
                _uiState.value = JournalUiState(entries)
            }
        }
    }

    fun clear() {
        viewModelScope.launch { repository.clear() }
    }
}
