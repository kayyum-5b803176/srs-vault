package com.srspassword.app.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.srspassword.app.data.*
import com.srspassword.app.algorithm.CardState
import com.srspassword.app.data.ReviewType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PasswordViewModel @Inject constructor(
    private val repository: PasswordRepository
) : ViewModel() {

    // ── Dashboard ─────────────────────────────────────────────────────────────
    val dashboardStats: StateFlow<DashboardStats> = repository.getDashboardStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats(0,0,0,0,0.0,0))

    val dueCards: StateFlow<List<PasswordCard>> = repository.getDueCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Card List ──────────────────────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val allCards: StateFlow<List<PasswordCard>> = _searchQuery
        .debounce(300)
        .flatMapLatest { q ->
            if (q.isBlank()) repository.getAllCards()
            else repository.searchCards(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<String>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Review Session ─────────────────────────────────────────────────────────
    private val _reviewQueue = MutableStateFlow<List<PasswordCard>>(emptyList())
    val reviewQueue: StateFlow<List<PasswordCard>> = _reviewQueue

    private val _currentReviewCard = MutableStateFlow<PasswordCard?>(null)
    val currentReviewCard: StateFlow<PasswordCard?> = _currentReviewCard

    private val _reviewSessionStats = MutableStateFlow(ReviewSessionStats())
    val reviewSessionStats: StateFlow<ReviewSessionStats> = _reviewSessionStats

    // ── UI State ───────────────────────────────────────────────────────────────
    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent

    private val _selectedCard = MutableStateFlow<PasswordCard?>(null)
    val selectedCard: StateFlow<PasswordCard?> = _selectedCard

    // ── Actions ────────────────────────────────────────────────────────────────

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun addCard(
        title: String, username: String, password: String,
        hint: String, category: String, tags: String,
        reviewType: ReviewType = ReviewType.VISUAL
    ) {
        viewModelScope.launch {
            repository.addPasswordCard(title, username, password, hint, category, tags, reviewType)
            _uiEvent.emit(UiEvent.ShowMessage("Password card added!"))
        }
    }

    fun updateCard(card: PasswordCard, newPassword: String? = null) {
        viewModelScope.launch {
            repository.updatePasswordCard(card, newPassword)
            _uiEvent.emit(UiEvent.ShowMessage("Card updated."))
        }
    }

    fun deleteCard(card: PasswordCard) {
        viewModelScope.launch {
            repository.deleteCard(card)
            _uiEvent.emit(UiEvent.ShowMessage("Card deleted."))
        }
    }

    fun loadCard(id: String) {
        viewModelScope.launch {
            _selectedCard.value = repository.getCardById(id)
        }
    }

    fun revealPassword(card: PasswordCard): String = repository.revealPassword(card)

    // ── Review ────────────────────────────────────────────────────────────────

    fun startReviewSession() {
        viewModelScope.launch {
            // Use .first() on the cold DB flow — never read .value off the StateFlow here
            // because SharingStarted.WhileSubscribed means the cache may still be emptyList()
            // at the moment the Review screen opens.
            val due = repository.getDueCards().first().toMutableList()
            // Mix in up to 10 new cards
            val newCards = repository.getNewCards().first().take(10)
            // Avoid duplicates (a NEW card could also be overdue)
            val newUnique = newCards.filter { n -> due.none { it.id == n.id } }
            due.addAll(newUnique)
            due.shuffle()
            _reviewQueue.value = due
            _currentReviewCard.value = due.firstOrNull()
            _reviewSessionStats.value = ReviewSessionStats(total = due.size)
        }
    }

    fun submitRating(card: PasswordCard, rating: Int) {
        viewModelScope.launch {
            repository.submitReview(card, rating)

            val stats = _reviewSessionStats.value
            _reviewSessionStats.value = stats.copy(
                reviewed  = stats.reviewed + 1,
                correct   = if (rating >= 3) stats.correct + 1 else stats.correct,
                again     = if (rating == 1) stats.again + 1 else stats.again
            )

            val queue = _reviewQueue.value.toMutableList()
            queue.remove(card)

            // If Again, re-queue at a later position
            if (rating == 1 && queue.size >= 3) {
                queue.add(minOf(3, queue.size), card)
            }

            _reviewQueue.value = queue
            _currentReviewCard.value = queue.firstOrNull()

            if (queue.isEmpty()) {
                _uiEvent.emit(UiEvent.ReviewSessionComplete(_reviewSessionStats.value))
            }
        }
    }

    // ── Export / Import ───────────────────────────────────────────────────────

    fun exportVault(passphrase: String, context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val data = repository.exportVault(passphrase)
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(data.toByteArray(Charsets.UTF_8))
                }
                _uiEvent.emit(UiEvent.ShowMessage("Vault exported successfully!"))
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.ShowMessage("Export failed: ${e.message}"))
            }
        }
    }

    fun importVault(passphrase: String, context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val data = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readText()
                    ?: return@launch

                when (val result = repository.importVault(data, passphrase)) {
                    is ImportResult.Success     ->
                        _uiEvent.emit(UiEvent.ShowMessage("Imported ${result.count} cards!"))
                    is ImportResult.WrongPassphrase ->
                        _uiEvent.emit(UiEvent.ShowMessage("Wrong passphrase — import failed."))
                    is ImportResult.Error ->
                        _uiEvent.emit(UiEvent.ShowMessage("Import error: ${result.message}"))
                }
            } catch (e: Exception) {
                _uiEvent.emit(UiEvent.ShowMessage("Import failed: ${e.message}"))
            }
        }
    }
}

data class ReviewSessionStats(
    val total: Int = 0,
    val reviewed: Int = 0,
    val correct: Int = 0,
    val again: Int = 0
)

sealed class UiEvent {
    data class ShowMessage(val message: String) : UiEvent()
    data class ReviewSessionComplete(val stats: ReviewSessionStats) : UiEvent()
}
