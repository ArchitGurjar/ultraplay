package com.ultrastream.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import com.ultrastream.app.utils.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager
) : ViewModel() {

    suspend fun exportBackup(): String {
        return backupManager.exportBackup()
    }

    suspend fun importBackup(json: String): Boolean {
        return backupManager.importBackup(json)
    }
}
