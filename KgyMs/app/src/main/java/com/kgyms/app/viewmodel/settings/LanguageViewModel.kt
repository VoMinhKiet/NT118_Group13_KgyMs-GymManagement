package com.kgyms.app.viewmodel.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kgyms.app.data.repository.LanguageRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LanguageViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LanguageRepository(application)

    /** "en" (mặc định) hoặc "vi". */
    val language: StateFlow<String> = repository.languageFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.currentLanguage())

    fun setLanguage(language: String) {
        repository.setLanguage(language)
    }
}
