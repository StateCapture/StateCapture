package za.co.statecapture.android.ui.tariffs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import za.co.statecapture.android.data.repository.TariffRepository
import za.co.statecapture.android.domain.model.TariffProvider

data class TariffUiState(
    val providers: List<TariffProvider> = emptyList(),
    val selectedProvider: TariffProvider? = null
)

class TariffViewModel(private val repository: TariffRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(TariffUiState())
    val uiState: StateFlow<TariffUiState> = _uiState.asStateFlow()

    init {
        loadTariffData()
    }

    private fun loadTariffData() {
        viewModelScope.launch {
            val providers = repository.getAllProviders()
            _uiState.update { it.copy(
                providers = providers,
                selectedProvider = providers.firstOrNull()
            ) }
        }
    }

    fun onProviderSelected(provider: TariffProvider) {
        _uiState.update { it.copy(selectedProvider = provider) }
    }
}
