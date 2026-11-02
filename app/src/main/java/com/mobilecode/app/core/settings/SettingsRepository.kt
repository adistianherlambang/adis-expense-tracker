package com.mobilecode.app.core.settings

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SettingsRepository(private val context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val settingsFile = File(context.filesDir, "settings.json")

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<IdeSettings> = _settings.asStateFlow()

    private fun loadSettings(): IdeSettings {
        return try {
            if (settingsFile.exists()) {
                json.decodeFromString<IdeSettings>(settingsFile.readText())
            } else {
                IdeSettings()
            }
        } catch (e: Exception) {
            IdeSettings()
        }
    }

    suspend fun updateSettings(newSettings: IdeSettings) = withContext(Dispatchers.IO) {
        try {
            settingsFile.writeText(json.encodeToString(newSettings))
            _settings.value = newSettings
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

