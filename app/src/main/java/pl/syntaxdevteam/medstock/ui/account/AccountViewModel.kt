package pl.syntaxdevteam.medstock.ui.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.core.account.AccountState
import pl.syntaxdevteam.medstock.core.account.AccountStateStore
import pl.syntaxdevteam.medstock.core.account.DriveBackupSnapshotRepository

class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private val accountStore = AccountStateStore(application)
    private val backupRepository = DriveBackupSnapshotRepository(application)

    private val _uiState = MutableLiveData<AccountUiState>()
    val uiState: LiveData<AccountUiState> = _uiState

    init {
        publishState(accountStore.getState())
    }

    fun connect(email: String) {
        accountStore.connect(email)
        publishState(accountStore.getState(), transientMessageRes = R.string.account_google_connected_message)
    }

    fun disconnect() {
        accountStore.disconnect()
        publishState(accountStore.getState(), transientMessageRes = R.string.account_google_disconnected_message)
    }

    fun setDriveBackupEnabled(enabled: Boolean) {
        val state = accountStore.getState()
        if (!state.isConnected) {
            publishState(state, transientMessageRes = R.string.account_drive_requires_google)
            return
        }
        accountStore.setDriveBackupEnabled(enabled)
        if (enabled) {
            createBackupSnapshot()
        } else {
            publishState(accountStore.getState(), transientMessageRes = R.string.account_drive_disabled_message)
        }
    }

    fun createBackupSnapshot() {
        val state = accountStore.getState()
        if (!state.isConnected) {
            publishState(state, transientMessageRes = R.string.account_drive_requires_google)
            return
        }
        _uiState.value = buildUiState(state, isBusy = true)
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                backupRepository.createSnapshotFile(state.email)
                accountStore.markBackupCreated()
                accountStore.getState()
            }
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { publishState(it, transientMessageRes = R.string.account_drive_snapshot_created_message) },
                    onFailure = { publishState(accountStore.getState(), transientMessageRes = R.string.account_drive_snapshot_failed_message) }
                )
            }
        }
    }

    fun messageShown() {
        _uiState.value = _uiState.value?.copy(transientMessageRes = null)
    }

    private fun publishState(
        state: AccountState,
        transientMessageRes: Int? = null,
    ) {
        _uiState.value = buildUiState(state, transientMessageRes = transientMessageRes)
    }

    private fun buildUiState(
        state: AccountState,
        isBusy: Boolean = false,
        transientMessageRes: Int? = null,
    ): AccountUiState {
        val context = getApplication<Application>()
        val lastBackup = state.formattedLastBackup()
        return AccountUiState(
            isConnected = state.isConnected,
            email = state.email,
            avatarLabel = state.avatarLabel,
            driveBackupEnabled = state.driveBackupEnabled,
            driveControlsEnabled = state.isConnected && !isBusy,
            isBusy = isBusy,
            accountStatusText = if (state.isConnected) {
                context.getString(R.string.account_google_connected_status, state.email)
            } else {
                context.getString(R.string.account_google_disconnected_status)
            },
            driveStatusText = when {
                !state.isConnected -> context.getString(R.string.account_drive_status_connect_first)
                state.driveBackupEnabled && lastBackup != null -> context.getString(R.string.account_drive_status_enabled_with_date, lastBackup)
                state.driveBackupEnabled -> context.getString(R.string.account_drive_status_enabled)
                else -> context.getString(R.string.account_drive_status_disabled)
            },
            transientMessageRes = transientMessageRes,
        )
    }
}

data class AccountUiState(
    val isConnected: Boolean,
    val email: String,
    val avatarLabel: String,
    val driveBackupEnabled: Boolean,
    val driveControlsEnabled: Boolean,
    val isBusy: Boolean,
    val accountStatusText: String,
    val driveStatusText: String,
    val transientMessageRes: Int?,
)
