package pl.syntaxdevteam.medstock.ui.account

import android.app.Application
import android.content.Intent
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
import pl.syntaxdevteam.medstock.core.account.BackupSnapshotMetadata
import pl.syntaxdevteam.medstock.core.account.DriveBackupAuthorizationRequiredException
import pl.syntaxdevteam.medstock.core.account.DriveBackupSnapshotRepository
import pl.syntaxdevteam.medstock.core.reminders.ReminderScheduler
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private val accountStore = AccountStateStore(application)
    private val backupRepository = DriveBackupSnapshotRepository(application)
    private val reminderScheduler = ReminderScheduler(application)

    private val _uiState = MutableLiveData<AccountUiState>()
    val uiState: LiveData<AccountUiState> = _uiState
    private var pendingDriveAction: PendingDriveAction? = null

    init {
        publishState(accountStore.getState())
    }

    fun connect(email: String) {
        accountStore.connect(email)
        val state = accountStore.getState()
        _uiState.value = buildUiState(state, isBusy = true)
        viewModelScope.launch(Dispatchers.IO) {
            findBackupAfterConnect(state)
        }
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

    fun restoreBackupSnapshot() {
        val state = accountStore.getState()
        if (!state.isConnected) {
            publishState(state, transientMessageRes = R.string.account_drive_requires_google)
            return
        }
        pendingDriveAction = PendingDriveAction.RESTORE_BACKUP
        _uiState.value = buildUiState(state, isBusy = true)
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                backupRepository.restoreLatestSnapshot(state.email)
                reminderScheduler.rescheduleAll()
                accountStore.markBackupCreated()
                accountStore.getState()
            }
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = {
                        pendingDriveAction = null
                        publishState(it, transientMessageRes = R.string.account_drive_restore_success_message)
                    },
                    onFailure = { throwable -> handleDriveFailure(throwable, R.string.account_drive_restore_failed_message) }
                )
            }
        }
    }

    fun dismissRestorePrompt() {
        _uiState.value = _uiState.value?.copy(restorePrompt = null)
    }

    fun createBackupSnapshot() {
        val state = accountStore.getState()
        if (!state.isConnected) {
            publishState(state, transientMessageRes = R.string.account_drive_requires_google)
            return
        }
        pendingDriveAction = PendingDriveAction.CREATE_BACKUP
        _uiState.value = buildUiState(state, isBusy = true)
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                backupRepository.createAndUploadSnapshot(state.email)
                accountStore.markBackupCreated()
                accountStore.getState()
            }
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = {
                        pendingDriveAction = null
                        publishState(it, transientMessageRes = R.string.account_drive_snapshot_uploaded_message)
                    },
                    onFailure = { throwable -> handleDriveFailure(throwable, R.string.account_drive_snapshot_failed_message) }
                )
            }
        }
    }

    fun driveAuthorizationLaunched() {
        _uiState.value = _uiState.value?.copy(driveAuthorizationIntent = null)
    }

    fun driveAuthorizationCancelled() {
        pendingDriveAction = null
        publishState(accountStore.getState(), transientMessageRes = R.string.account_drive_authorization_cancelled_message)
    }

    fun retryPendingDriveAction() {
        when (pendingDriveAction) {
            PendingDriveAction.CONNECT_LOOKUP -> {
                val state = accountStore.getState()
                _uiState.value = buildUiState(state, isBusy = true)
                viewModelScope.launch(Dispatchers.IO) { findBackupAfterConnect(state) }
            }
            PendingDriveAction.CREATE_BACKUP -> createBackupSnapshot()
            PendingDriveAction.RESTORE_BACKUP -> restoreBackupSnapshot()
            null -> publishState(accountStore.getState())
        }
    }

    fun messageShown() {
        _uiState.value = _uiState.value?.copy(transientMessageRes = null)
    }

    private suspend fun findBackupAfterConnect(state: AccountState) {
        pendingDriveAction = PendingDriveAction.CONNECT_LOOKUP
        val result = runCatching {
            backupRepository.findRestorableSnapshot(state.email)?.let { metadata ->
                AccountRestorePrompt(
                    createdAtText = metadata.formattedCreatedAt(),
                    medicationCount = metadata.medicationCount,
                    reminderCount = metadata.reminderCount,
                )
            }
        }
        withContext(Dispatchers.Main) {
            result.fold(
                onSuccess = { restorePrompt ->
                    pendingDriveAction = null
                    publishState(
                        state = accountStore.getState(),
                        transientMessageRes = if (restorePrompt == null) R.string.account_drive_restore_no_backup_message else null,
                        restorePrompt = restorePrompt,
                    )
                },
                onFailure = { throwable -> handleDriveFailure(throwable, R.string.account_drive_restore_failed_message) }
            )
        }
    }

    private fun handleDriveFailure(throwable: Throwable, fallbackMessageRes: Int) {
        val state = accountStore.getState()
        if (throwable is DriveBackupAuthorizationRequiredException && throwable.authorizationIntent != null) {
            _uiState.value = buildUiState(
                state = state,
                transientMessageRes = R.string.account_drive_authorization_required_message,
                driveAuthorizationIntent = throwable.authorizationIntent,
            )
        } else {
            pendingDriveAction = null
            publishState(state, transientMessageRes = fallbackMessageRes)
        }
    }

    private fun publishState(
        state: AccountState,
        transientMessageRes: Int? = null,
        restorePrompt: AccountRestorePrompt? = null,
    ) {
        _uiState.value = buildUiState(
            state = state,
            transientMessageRes = transientMessageRes,
            restorePrompt = restorePrompt,
        )
    }

    private fun buildUiState(
        state: AccountState,
        isBusy: Boolean = false,
        transientMessageRes: Int? = null,
        restorePrompt: AccountRestorePrompt? = null,
        driveAuthorizationIntent: Intent? = null,
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
            restorePrompt = restorePrompt,
            driveAuthorizationIntent = driveAuthorizationIntent,
        )
    }

    private fun BackupSnapshotMetadata.formattedCreatedAt(zoneId: ZoneId = ZoneId.systemDefault()): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .format(Instant.ofEpochMilli(createdAtUtc).atZone(zoneId))
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
    val restorePrompt: AccountRestorePrompt?,
    val driveAuthorizationIntent: Intent?,
)

private enum class PendingDriveAction {
    CONNECT_LOOKUP,
    CREATE_BACKUP,
    RESTORE_BACKUP,
}

data class AccountRestorePrompt(
    val createdAtText: String,
    val medicationCount: Int,
    val reminderCount: Int,
)
