package com.example.presentation.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.CloudSession
import com.example.domain.model.SyncSummary
import com.example.domain.repository.CloudRepository
import com.example.domain.usecase.RegisterAccountUseCase
import com.example.domain.usecase.SignInUseCase
import com.example.domain.usecase.SynchronizeLearningDataUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

data class CloudSyncUiState(
    val session: CloudSession? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val summary: SyncSummary? = null
)

class CloudSyncViewModel(private val repository: CloudRepository) : ViewModel() {
    private val registerAccount = RegisterAccountUseCase(repository)
    private val signInAccount = SignInUseCase(repository)
    private val synchronizeData = SynchronizeLearningDataUseCase(repository)
    private val _state = MutableStateFlow(CloudSyncUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.session.collect { session ->
                _state.update { it.copy(session = session) }
            }
        }
    }

    fun authenticate(email: String, password: String, createAccount: Boolean) {
        if (email.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "Email va parolni kiriting.", message = null) }
            return
        }
        launchAction {
            if (createAccount) registerAccount(email, password) else signInAccount(email, password)
            val result = synchronizeData(progressReporter)
            _state.update {
                it.copy(
                    message = "Kirish muvaffaqiyatli. ${result.savedWords} ta so‘z sinxronlandi.",
                    summary = result
                )
            }
        }
    }

    fun synchronize() {
        launchAction {
            val result = synchronizeData(progressReporter)
            _state.update {
                it.copy(
                    message = "Sinxronlash tugadi: ${result.savedWords} ta so‘z, " +
                        "${result.quizAttempts} ta test natijasi.",
                    summary = result
                )
            }
        }
    }

    fun signOut() {
        launchAction {
            repository.signOut()
            _state.update { it.copy(message = "Hisobdan chiqildi.", summary = null) }
        }
    }

    private val progressReporter: (Int, Int) -> Unit = { synced, total ->
        if (total > 0) {
            _state.update { it.copy(message = "Lug‘at yuklanmoqda: $synced / $total") }
        }
    }

    private fun launchAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, message = null) }
            try {
                action()
            } catch (error: IOException) {
                _state.update { it.copy(error = "Tarmoq bilan bog‘lanib bo‘lmadi: ${error.message}") }
            } catch (error: HttpException) {
                _state.update {
                    it.copy(error = "Server xatosi (${error.code()}): ${error.message()}")
                }
            } catch (error: IllegalArgumentException) {
                _state.update { it.copy(error = error.message ?: "Yuborilgan ma’lumot noto‘g‘ri.") }
            } catch (error: IllegalStateException) {
                _state.update { it.copy(error = error.message ?: "Bu amalni bajarib bo‘lmadi.") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }
}
