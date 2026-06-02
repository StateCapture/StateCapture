package za.co.statecapture.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import za.co.statecapture.android.data.AppDatabase
import za.co.statecapture.android.data.repository.SettingsRepository
import za.co.statecapture.android.data.repository.TariffRepository
import za.co.statecapture.android.ui.calculator.CalculationViewModel
import za.co.statecapture.android.ui.dashboard.DashboardViewModel
import za.co.statecapture.android.ui.meters.MeterViewModel
import za.co.statecapture.android.ui.settings.SettingsViewModel
import za.co.statecapture.android.ui.tariffs.TariffViewModel

class AppViewModelFactory(
    private val database: AppDatabase,
    private val tariffRepository: TariffRepository,
    private val settingsRepository: SettingsRepository? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MeterViewModel::class.java) ->
                MeterViewModel(database.meterDao(), tariffRepository) as T
            modelClass.isAssignableFrom(CalculationViewModel::class.java) ->
                CalculationViewModel(tariffRepository, database.purchaseDao()) as T
            modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                DashboardViewModel(database.purchaseDao()) as T
            modelClass.isAssignableFrom(TariffViewModel::class.java) ->
                TariffViewModel(tariffRepository) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(settingsRepository!!) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
