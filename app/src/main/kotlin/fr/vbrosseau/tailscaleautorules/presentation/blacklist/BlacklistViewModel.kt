package fr.vbrosseau.tailscaleautorules.presentation.blacklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.tailscaleautorules.domain.model.asSsidKey
import fr.vbrosseau.tailscaleautorules.domain.network.NetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.BlacklistRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.DuplicateSsidException
import fr.vbrosseau.tailscaleautorules.presentation.SystemStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Alimente l'écran des réseaux de confiance.
 *
 * Les règles — unicité, forme canonique — appartiennent au repository. Ce
 * ViewModel se contente de **traduire** ses échecs en quelque chose que
 * l'interface sait afficher.
 */
@HiltViewModel
class BlacklistViewModel @Inject constructor(
    private val repository: BlacklistRepository,
    private val systemStatus: SystemStatus,
    networkObserver: NetworkObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlacklistUiState())
    val uiState: StateFlow<BlacklistUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observeAll(),
                networkObserver.observe(),
            ) { entries, network -> entries to network.ssid }
                .collect { (entries, ssid) ->
                    _uiState.value = _uiState.value.copy(
                        entries = entries,
                        currentSsid = ssid,
                        isCurrentSsidAlreadyListed = ssid != null &&
                            entries.any { it.value.asSsidKey() == ssid.asSsidKey() },
                    )
                }
        }
        refreshSystemStatus()
    }

    /**
     * Relit l'autorisation de lecture du SSID.
     *
     * Elle s'accorde dans les réglages système : sans reconstat au retour à
     * l'écran, l'explication resterait affichée alors que la permission vient
     * d'être donnée.
     */
    fun refreshSystemStatus() {
        _uiState.value = _uiState.value.copy(canReadSsid = systemStatus.canReadSsid())
    }

    fun add(ssid: String) {
        submit { repository.add(ssid) }
    }

    /** Ajoute le réseau auquel le terminal est connecté. */
    fun addCurrentSsid() {
        val ssid = _uiState.value.currentSsid ?: return
        add(ssid)
    }

    fun rename(id: Long, ssid: String) {
        submit { repository.update(id, ssid) }
    }

    fun remove(id: Long) {
        viewModelScope.launch { repository.remove(id) }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun submit(action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = action().toError())
        }
    }

    private fun Result<Unit>.toError(): BlacklistError? = exceptionOrNull()?.let { cause ->
        when (cause) {
            is DuplicateSsidException -> BlacklistError.DUPLICATE
            is IllegalArgumentException -> BlacklistError.BLANK
            else -> BlacklistError.UNKNOWN
        }
    }
}
