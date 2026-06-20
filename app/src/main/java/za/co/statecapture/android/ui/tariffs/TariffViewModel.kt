package za.co.statecapture.android.ui.tariffs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import za.co.statecapture.android.data.repository.TariffRepository
import za.co.statecapture.android.domain.model.TariffProvider

import za.co.statecapture.android.domain.model.TariffIndexItem

data class TariffUiState(
    val providers: List<TariffIndexItem> = emptyList(),
    val selectedProvider: TariffProvider? = null,
    val selectedIndexItem: TariffIndexItem? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class TariffViewModel(private val repository: TariffRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(TariffUiState())
    val uiState: StateFlow<TariffUiState> = _uiState.asStateFlow()

    init {
        loadTariffData()
    }

    private fun loadTariffData() {
        viewModelScope.launch {
            try {
                val providers = repository.getAllProviders()
                _uiState.update { it.copy(providers = providers) }
                if (providers.isNotEmpty()) {
                    onProviderSelected(providers.first())
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load index") }
            }
        }
    }

    fun onProviderSelected(indexItem: TariffIndexItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedIndexItem = indexItem, isLoading = true, error = null) }
            try {
                val provider = repository.getProvider(indexItem.id)
                if (provider != null) {
                    _uiState.update { it.copy(selectedProvider = provider, isLoading = false) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Provider data not found") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load provider data") }
            }
        }
    }
}
