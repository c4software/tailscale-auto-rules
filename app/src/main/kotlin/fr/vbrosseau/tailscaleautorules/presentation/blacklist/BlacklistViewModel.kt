package fr.vbrosseau.tailscaleautorules.presentation.blacklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.vbrosseau.tailscaleautorules.domain.model.asSsidKey
import fr.vbrosseau.tailscaleautorules.domain.network.NetworkObserver
import fr.vbrosseau.tailscaleautorules.domain.repository.BlacklistRepository
import fr.vbrosseau.tailscaleautorules.domain.repository.DuplicateSsidException
import fr.vbrosseau.tailscaleautorules.presentation.SystemStatus
import fr.vbrosseau.tailscaleautorules.presentation.UiStateSharing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
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

    private val error = MutableStateFlow<BlacklistError?>(null)
    private val canReadSsid = MutableStateFlow(systemStatus.canReadSsid())

    /**
     * Le SSID courant n'enrichit que l'ajout rapide : il démarre à « inconnu »
     * pour que la liste ne l'attende jamais. Le flux réseau est stabilisé par
     * une fenêtre de deux secondes et reste muet tant qu'aucun réseau ne
     * correspond — en faire une condition d'affichage retenait l'écran entier.
     */
    private val currentSsid = networkObserver.observe()
        .map { it.ssid }
        .onStart { emit(null) }

    /**
     * Publié en `WhileSubscribed` : l'observation du réseau ne vit que
     * lorsqu'un écran regarde.
     */
    val uiState: StateFlow<BlacklistUiState> = combine(
        repository.observeAll(),
        currentSsid,
        canReadSsid,
        error,
    ) { entries, ssid, canRead, currentError ->
        BlacklistUiState(
            entries = entries,
            currentSsid = ssid,
            isCurrentSsidAlreadyListed = ssid != null &&
                entries.any { it.value.asSsidKey() == ssid.asSsidKey() },
            canReadSsid = canRead,
            error = currentError,
        )
    }.stateIn(viewModelScope, UiStateSharing, BlacklistUiState(isLoading = true))

    /**
     * Relit l'autorisation de lecture du SSID.
     *
     * Elle s'accorde dans les réglages système : sans reconstat au retour à
     * l'écran, l'explication resterait affichée alors que la permission vient
     * d'être donnée.
     */
    fun refreshSystemStatus() {
        canReadSsid.value = systemStatus.canReadSsid()
    }

    fun add(ssid: String) {
        submit { repository.add(ssid) }
    }

    /** Ajoute le réseau auquel le terminal est connecté. */
    fun addCurrentSsid() {
        val ssid = uiState.value.currentSsid ?: return
        add(ssid)
    }

    fun rename(id: Long, ssid: String) {
        submit { repository.update(id, ssid) }
    }

    fun remove(id: Long) {
        viewModelScope.launch { repository.remove(id) }
    }

    fun dismissError() {
        error.value = null
    }

    private fun submit(action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            error.value = action().toError()
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
