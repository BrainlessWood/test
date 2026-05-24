package com.example.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsHelper(context: Context) {
    private val prefs = context.getSharedPreferences("drive_reader_prefs", Context.MODE_PRIVATE)

    private val _accessToken = MutableStateFlow(prefs.getString(KEY_ACCESS_TOKEN, "") ?: "")
    val accessToken: StateFlow<String> = _accessToken

    private val _ttsSpeed = MutableStateFlow(prefs.getFloat(KEY_TTS_SPEED, 1.0f))
    val ttsSpeed: StateFlow<Float> = _ttsSpeed

    private val _ttsPitch = MutableStateFlow(prefs.getFloat(KEY_TTS_PITCH, 1.0f))
    val ttsPitch: StateFlow<Float> = _ttsPitch

    private val _selectedFolderId = MutableStateFlow(prefs.getString(KEY_FOLDER_ID, "") ?: "")
    val selectedFolderId: StateFlow<String> = _selectedFolderId

    private val _selectedFolderName = MutableStateFlow(prefs.getString(KEY_FOLDER_NAME, "") ?: "")
    val selectedFolderName: StateFlow<String> = _selectedFolderName

    fun saveAccessToken(token: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
        _accessToken.value = token
    }

    fun saveTtsSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_TTS_SPEED, speed).apply()
        _ttsSpeed.value = speed
    }

    fun saveTtsPitch(pitch: Float) {
        prefs.edit().putFloat(KEY_TTS_PITCH, pitch).apply()
        _ttsPitch.value = pitch
    }

    fun saveSelectedFolder(id: String, name: String) {
        prefs.edit().putString(KEY_FOLDER_ID, id).putString(KEY_FOLDER_NAME, name).apply()
        _selectedFolderId.value = id
        _selectedFolderName.value = name
    }

    fun clearSession() {
        prefs.edit().remove(KEY_ACCESS_TOKEN).apply()
        _accessToken.value = ""
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_TTS_SPEED = "tts_speed"
        private const val KEY_TTS_PITCH = "tts_pitch"
        private const val KEY_FOLDER_ID = "selected_folder_id"
        private const val KEY_FOLDER_NAME = "selected_folder_name"
    }
}
