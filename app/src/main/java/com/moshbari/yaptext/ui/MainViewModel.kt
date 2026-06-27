package com.moshbari.yaptext.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moshbari.yaptext.data.AppStorage
import com.moshbari.yaptext.data.Language
import com.moshbari.yaptext.data.PolishService
import com.moshbari.yaptext.data.SubscriptionManager
import com.moshbari.yaptext.data.Tone
import com.moshbari.yaptext.data.TranscriptionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val transcription = TranscriptionService(app, viewModelScope)
    val transcriptionState = transcription.state

    private val polishService = PolishService()

    private val _polishedText = MutableStateFlow("")
    val polishedText = _polishedText.asStateFlow()

    private val _isPolishing = MutableStateFlow(false)
    val isPolishing = _isPolishing.asStateFlow()

    private val _polishError = MutableStateFlow<String?>(null)
    val polishError = _polishError.asStateFlow()

    /** Editable copy of the transcription — the user can fix words before polishing. */
    private val _editableText = MutableStateFlow("")
    val editableText = _editableText.asStateFlow()
    private var lastSeeded = ""

    init {
        // Seed the editor each time a NEW transcription arrives (status-only
        // updates keep the same text, so user edits aren't clobbered).
        viewModelScope.launch {
            transcriptionState.collect { st ->
                if (st.transcribedText != lastSeeded) {
                    lastSeeded = st.transcribedText
                    _editableText.value = st.transcribedText
                }
            }
        }
    }

    fun onEditText(text: String) { _editableText.value = text }

    private val _language = MutableStateFlow(AppStorage.language)
    val language = _language.asStateFlow()

    val isPro = SubscriptionManager.isPro
    val trialTick = SubscriptionManager.trialTick

    fun setLanguage(lang: Language) {
        _language.value = lang
        AppStorage.language = lang
    }

    /** Reset polish state before a fresh recording. */
    fun resetPolish() {
        _polishedText.value = ""
        _polishError.value = null
    }

    fun polish(tone: Tone) {
        val text = _editableText.value.ifBlank { transcriptionState.value.transcribedText }
        if (text.isBlank()) return
        _isPolishing.value = true
        _polishedText.value = ""
        _polishError.value = null
        viewModelScope.launch {
            when (val result = polishService.polish(text, tone)) {
                is PolishService.Result.Success -> _polishedText.value = result.text
                is PolishService.Result.Failure -> _polishError.value = result.message
            }
            _isPolishing.value = false
        }
    }

    fun refreshSubscription() = SubscriptionManager.refresh()
}
