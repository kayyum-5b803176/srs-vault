package com.srspassword.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srspassword.app.data.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences
) : ViewModel() {

    val stealthMode: StateFlow<Boolean> = prefs.stealthMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setStealthMode(enabled: Boolean) {
        viewModelScope.launch { prefs.setStealthMode(enabled) }
    }
}
