package za.co.statecapture.android.ui.meters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import za.co.statecapture.android.data.Meter
import za.co.statecapture.android.data.MeterDao
import za.co.statecapture.android.data.repository.TariffRepository
import za.co.statecapture.android.domain.model.TariffIndexItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MeterViewModel(
    private val dao: MeterDao,
    private val repository: TariffRepository
) : ViewModel() {

    val meters: StateFlow<List<Meter>> = dao.getAllMeters()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _availableProviders = MutableStateFlow<List<TariffIndexItem>>(emptyList())
    val availableProviders: StateFlow<List<TariffIndexItem>> = _availableProviders.asStateFlow()

    init {
        loadProviders()
    }

    private fun loadProviders() {
        viewModelScope.launch {
            _availableProviders.value = repository.getAllProviders()
        }
    }

    fun addMeter(name: String, meterNumber: String, providerId: String, isDefault: Boolean, icon: String = "⚡") {
        viewModelScope.launch {
            // Proactively fetch the provider so it is cached
            repository.getProvider(providerId)
            
            val nextOrder = (meters.value.maxByOrNull { it.displayOrder }?.displayOrder ?: -1) + 1
            dao.insertMeter(
                Meter(
                    name = name,
                    meterNumber = meterNumber,
                    providerId = providerId,
                    isDefault = isDefault,
                    icon = icon,
                    displayOrder = nextOrder
                )
            )
        }
    }

    fun reorderMeters(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val list = meters.value.toMutableList()
            if (fromIndex !in list.indices || toIndex !in list.indices) return@launch
            
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            
            // Re-assign display orders based on new positions
            val updatedList = list.mapIndexed { index, meter ->
                meter.copy(displayOrder = index)
            }
            dao.updateMeters(updatedList)
        }
    }

    fun updateMeter(meter: Meter) {
        viewModelScope.launch {
            dao.updateMeter(meter)
        }
    }

    fun deleteMeter(meter: Meter) {
        viewModelScope.launch {
            dao.deleteMeter(meter)
        }
    }
}
