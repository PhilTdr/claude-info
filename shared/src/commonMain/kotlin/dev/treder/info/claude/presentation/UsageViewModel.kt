package dev.treder.info.claude.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.treder.info.claude.domain.model.PricingState
import dev.treder.info.claude.domain.repository.PricingRepository
import dev.treder.info.claude.domain.repository.UpdateRepository
import dev.treder.info.claude.domain.repository.UsageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UsageViewModel(
    private val usageRepository: UsageRepository,
    private val pricingRepository: PricingRepository,
    private val updateRepository: UpdateRepository,
    private val preferredModelProvider: suspend () -> String? = { null },
) : ViewModel() {

    private val _state = MutableStateFlow(UsageUiState())
    val state: StateFlow<UsageUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val preferred = runCatching { preferredModelProvider() }.getOrNull()
            if (preferred != null) {
                _state.update { it.copy(preferredModel = preferred) }
            }
        }
        viewModelScope.launch {
            pricingRepository.state.collect { pricing ->
                _state.update { it.copy(pricingPhase = pricing.toPhase()) }
            }
        }
        viewModelScope.launch {
            updateRepository.isUpdateAvailable.collect { status ->
                _state.update { it.copy(updateStatus = status) }
            }
        }
        viewModelScope.launch {
            usageRepository.getTodayUsage()
                .catch { e -> _state.update { it.copy(error = e.message ?: "Unbekannter Fehler") } }
                .collect { day -> _state.update { it.copy(today = day, error = null) } }
        }
        viewModelScope.launch {
            usageRepository.getHistoryUsage()
                .catch { e -> _state.update { it.copy(error = e.message ?: "Unbekannter Fehler") } }
                .collect { history -> _state.update { it.copy(history = history, error = null) } }
        }
    }

    /** Re-attempt the pricing fetch, e.g. after the first attempt failed. */
    fun retryPricing() = pricingRepository.refresh()

    private fun PricingState.toPhase(): PricingPhase = when (this) {
        is PricingState.Loading -> PricingPhase.Loading
        is PricingState.Ready -> PricingPhase.Ready
        is PricingState.Failed -> PricingPhase.Failed
    }
}
