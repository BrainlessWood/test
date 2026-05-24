package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsHelper
import com.example.data.remote.DriveApiService
import com.example.data.remote.DriveFile
import com.example.data.repository.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log

class DriveViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val apiService = DriveApiService.create()
    val repository = BookRepository(application, db.bookDao(), db.folderDao(), apiService)
    val settingsHelper = SettingsHelper(application)

    // Filter out temporary stream books from the users' Local Library list
    val localBooks: StateFlow<List<com.example.data.model.Book>> = repository.allBooks.map { list ->
        list.filter { it.folderId != "temp" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedFolders = repository.allFolders

    private val _foldersState = MutableStateFlow<UiState<List<DriveFile>>>(UiState.Idle)
    val foldersState: StateFlow<UiState<List<DriveFile>>> = _foldersState

    private val _filesState = MutableStateFlow<UiState<List<DriveFile>>>(UiState.Idle)
    val filesState: StateFlow<UiState<List<DriveFile>>> = _filesState

    // Track Google OAuth Sign-In values
    val userEmail = MutableStateFlow(settingsHelper.accessToken.value.let { if (it.isNotEmpty()) "ramazanenes1983@gmail.com" else "" })
    val isSigningIn = MutableStateFlow(false)

    // State of streaming download progress (0f..1f)
    val streamingProgress = MutableStateFlow<Float?>(null)

    sealed interface UiState<out T> {
        object Idle : UiState<Nothing>
        object Loading : UiState<Nothing>
        data class Success<T>(val data: T) : UiState<T>
        data class Error(val message: String) : UiState<Nothing>
    }

    init {
        // Clear cached temp files from disk on start to keep device cache clean
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cacheDir = getApplication<Application>().cacheDir
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("temp_") && file.name.endsWith(".pdf")) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                Log.e("DriveViewModel", "Failed to clean up temp files: ${e.message}")
            }
        }
    }

    fun saveToken(token: String) {
        settingsHelper.saveAccessToken(token)
        if (token.isNotEmpty()) {
            userEmail.value = "ramazanenes1983@gmail.com"
        } else {
            userEmail.value = ""
        }
    }

    fun googleSignIn(email: String, generatedToken: String) {
        settingsHelper.saveAccessToken(generatedToken)
        userEmail.value = email
    }

    fun clearSession() {
        settingsHelper.clearSession()
        userEmail.value = ""
    }

    fun selectFolder(id: String, name: String) {
        settingsHelper.saveSelectedFolder(id, name)
        viewModelScope.launch {
            repository.saveFolder(id, name)
            fetchFilesForFolder(id)
        }
    }

    fun fetchDriveFolders() {
        val token = settingsHelper.accessToken.value
        if (token.isEmpty()) {
            _foldersState.value = UiState.Error("Please enter a Google Drive OAuth access token.")
            return
        }

        _foldersState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val list = repository.listFoldersFromDrive(token)
                _foldersState.value = UiState.Success(list)
            } catch (e: Exception) {
                _foldersState.value = UiState.Error(e.message ?: "Could not fetch folders. Please check your token.")
            }
        }
    }

    fun fetchFilesForFolder(folderId: String) {
        val token = settingsHelper.accessToken.value
        if (token.isEmpty()) {
            _filesState.value = UiState.Error("Please enter a Google Drive OAuth access token.")
            return
        }

        _filesState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val list = repository.listPdfFilesFromDrive(token, folderId)
                _filesState.value = UiState.Success(list)
            } catch (e: Exception) {
                _filesState.value = UiState.Error(e.message ?: "Failed to load PDFs.")
            }
        }
    }

    fun importPdfFile(driveFile: DriveFile, folderId: String) {
        viewModelScope.launch {
            repository.importDrivePdf(driveFile, folderId)
        }
    }

    fun downloadBook(bookId: String) {
        val token = settingsHelper.accessToken.value
        if (token.isEmpty()) return
        viewModelScope.launch {
            repository.downloadBookPdf(token, bookId)
        }
    }

    // Stream a PDF file immediately from Google Drive into cacheDir (Online Mode)
    fun streamBookOnline(driveFile: DriveFile, onComplete: (String) -> Unit) {
        val token = settingsHelper.accessToken.value
        if (token.isEmpty()) return
        streamingProgress.value = 0.01f

        viewModelScope.launch {
            try {
                val response = apiService.downloadFile("Bearer $token", driveFile.id)
                if (!response.isSuccessful) {
                    streamingProgress.value = null
                    return@launch
                }
                val body = response.body()
                if (body == null) {
                    streamingProgress.value = null
                    return@launch
                }

                val cacheDir = getApplication<Application>().cacheDir
                val tempFile = File(cacheDir, "temp_${driveFile.id}.pdf")
                val totalBytes = body.contentLength()
                var bytesCopied = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytes = input.read(buffer)
                        var updateTimer = 0L
                        while (bytes >= 0) {
                            output.write(buffer, 0, bytes)
                            bytesCopied += bytes
                            bytes = input.read(buffer)

                            val now = System.currentTimeMillis()
                            if (now - updateTimer > 150 && totalBytes > 0) {
                                updateTimer = now
                                streamingProgress.value = bytesCopied.toFloat() / totalBytes
                            }
                        }
                    }
                }

                // Calculate pages dynamically
                var pages = 0
                try {
                    val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    pages = renderer.pageCount
                    renderer.close()
                    pfd.close()
                } catch (e: Exception) {
                    Log.e("DriveViewModel", "Temp page count extraction failed: ${e.message}")
                }

                // Insert dynamic ephemerally cached book record with folderId = "temp"
                val tempBook = com.example.data.model.Book(
                    id = driveFile.id,
                    title = driveFile.name,
                    folderId = "temp",
                    localPath = tempFile.absolutePath,
                    currentPage = 0,
                    totalPages = pages,
                    isCached = true, // Set to true so ReaderScreen doesn't prompt download
                    downloadProgress = 1.0f
                )

                repository.importDrivePdf(driveFile, "temp")
                db.bookDao().insertBook(tempBook)

                streamingProgress.value = null
                onComplete(driveFile.id)
            } catch (e: Exception) {
                Log.e("DriveViewModel", "Error streaming online PDF file: ${e.message}")
                streamingProgress.value = null
            }
        }
    }
}

