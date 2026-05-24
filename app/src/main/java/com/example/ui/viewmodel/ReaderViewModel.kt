package com.example.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsHelper
import com.example.data.model.Book
import com.example.data.remote.DriveApiService
import com.example.data.repository.BookRepository
import com.example.service.TtsService
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val apiService = DriveApiService.create()
    private val repository = BookRepository(application, db.bookDao(), db.folderDao(), apiService)
    val settingsHelper = SettingsHelper(application)

    private val _currentBook = MutableStateFlow<Book?>(null)
    val currentBook: StateFlow<Book?> = _currentBook

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage

    private val _pageBitmap = MutableStateFlow<Bitmap?>(null)
    val pageBitmap: StateFlow<Bitmap?> = _pageBitmap

    private val _isLoadingPage = MutableStateFlow(false)
    val isLoadingPage: StateFlow<Boolean> = _isLoadingPage

    // Service binding
    private var ttsService: TtsService? = null
    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound: StateFlow<Boolean> = _isServiceBound

    // Expose TTS service playback states
    val isTtsPlaying: StateFlow<Boolean> = _isServiceBound.flatMapLatest { bound ->
        if (bound) ttsService?.isPlaying ?: MutableStateFlow(false) else MutableStateFlow(false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val currentTtsIndex: StateFlow<Int> = _isServiceBound.flatMapLatest { bound ->
        if (bound) ttsService?.currentSentenceIndex ?: MutableStateFlow(0) else MutableStateFlow(0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val turkishStatus: StateFlow<String> = _isServiceBound.flatMapLatest { bound ->
        if (bound) ttsService?.turkishStatus ?: MutableStateFlow("UNKNOWN") else MutableStateFlow("UNKNOWN")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "UNKNOWN")

    private val _pageSentences = MutableStateFlow<List<String>>(emptyList())
    val pageSentences: StateFlow<List<String>> = _pageSentences

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TtsService.TtsBinder
            ttsService = binder.getService()
            _isServiceBound.value = true
            ttsService?.setSpeedAndPitch(settingsHelper.ttsSpeed.value, settingsHelper.ttsPitch.value)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            ttsService = null
            _isServiceBound.value = false
        }
    }

    init {
        // Initialize PDFbox Android
        try {
            PDFBoxResourceLoader.init(application)
        } catch (e: Throwable) {
            Log.e("ReaderViewModel", "PDFBox init failed: ${e.message}")
        }
        bindTtsService()
    }

    private fun bindTtsService() {
        val intent = Intent(getApplication(), TtsService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun loadBook(bookId: String) {
        viewModelScope.launch {
            repository.getBookById(bookId).collect { book ->
                if (book != null) {
                    _currentBook.value = book
                    _currentPage.value = book.currentPage
                    loadAndRenderPage(book.currentPage)
                }
            }
        }
    }

    fun nextPage() {
        val book = _currentBook.value ?: return
        val current = _currentPage.value
        if (current + 1 < book.totalPages) {
            val next = current + 1
            _currentPage.value = next
            viewModelScope.launch {
                repository.updateBookProgress(book.id, next)
                loadAndRenderPage(next)
            }
        }
    }

    fun prevPage() {
        val book = _currentBook.value ?: return
        val current = _currentPage.value
        if (current > 0) {
            val prev = current - 1
            _currentPage.value = prev
            viewModelScope.launch {
                repository.updateBookProgress(book.id, prev)
                loadAndRenderPage(prev)
            }
        }
    }

    fun goToPage(page: Int) {
        val book = _currentBook.value ?: return
        if (page in 0 until book.totalPages) {
            _currentPage.value = page
            viewModelScope.launch {
                repository.updateBookProgress(book.id, page)
                loadAndRenderPage(page)
            }
        }
    }

    private var renderJob: kotlinx.coroutines.Job? = null

    fun loadAndRenderPage(pageIndex: Int) {
        _isLoadingPage.value = true
        // Reset TTS text state of the new page
        _pageSentences.value = emptyList()
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            val bitmap = renderPdfPage(pageIndex)
            _pageBitmap.value = bitmap
            _isLoadingPage.value = false
        }
    }

    private suspend fun renderPdfPage(pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
        val book = _currentBook.value ?: return@withContext null
        val path = book.localPath ?: return@withContext null
        try {
            val file = File(path)
            if (!file.exists()) return@withContext null
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                renderer.close()
                pfd.close()
                return@withContext null
            }
            val page = renderer.openPage(pageIndex)
            
            // Limit page layout rendering dimensions to ensure we do not exceed memory limitations
            var width = page.width * 2
            var height = page.height * 2
            val maxDimension = 2048
            if (width > maxDimension || height > maxDimension) {
                val scale = maxDimension.toFloat() / maxOf(width, height)
                width = (width * scale).toInt()
                height = (height * scale).toInt()
            }
            if (width <= 0) width = 1
            if (height <= 0) height = 1

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()
            bitmap
        } catch (e: Throwable) {
            Log.e("ReaderViewModel", "Native PdfRenderer rendering failed: ${e.message}")
            null
        }
    }

    // TTS Control Actions
    fun triggerReadAloud() {
        val book = _currentBook.value ?: return
        val page = _currentPage.value
        
        viewModelScope.launch {
            _isLoadingPage.value = true
            val pageText = extractTextFromPage(page)
            val sentences = chunkTextIntoSentences(pageText)
            _pageSentences.value = sentences
            _isLoadingPage.value = false

            if (sentences.isNotEmpty()) {
                val serviceIntent = Intent(getApplication(), TtsService::class.java)
                getApplication<Application>().startService(serviceIntent)
                ttsService?.playText(book.title, page, sentences)
            } else {
                Log.e("ReaderViewModel", "No text found on page to read aloud.")
            }
        }
    }

    fun pauseTts() {
        ttsService?.pausePlayback()
    }

    fun resumeTts() {
        ttsService?.resumePlayback()
    }

    fun stopTts() {
        ttsService?.stopPlayback()
    }

    fun adjustSpeed(speed: Float) {
        settingsHelper.saveTtsSpeed(speed)
        ttsService?.setSpeedAndPitch(speed, settingsHelper.ttsPitch.value)
    }

    fun adjustPitch(pitch: Float) {
        settingsHelper.saveTtsPitch(pitch)
        ttsService?.setSpeedAndPitch(settingsHelper.ttsSpeed.value, pitch)
    }

    fun checkTurkishTtsSupport(): String {
        return ttsService?.checkTurkishLanguagePack() ?: "UNKNOWN"
    }

    private suspend fun extractTextFromPage(pageIndex: Int): String = withContext(Dispatchers.IO) {
        val book = _currentBook.value ?: return@withContext ""
        val path = book.localPath ?: return@withContext ""
        try {
            val file = File(path)
            val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(file)
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            stripper.startPage = pageIndex + 1
            stripper.endPage = pageIndex + 1
            val text = stripper.getText(document) ?: ""
            document.close()
            text
        } catch (e: Throwable) {
            Log.e("ReaderViewModel", "Failed to extract text using PDFBox: ${e.message}")
            ""
        }
    }

    private fun chunkTextIntoSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val cleaned = text.replace(Regex("[\\r\\n\\t\\s]+"), " ").trim()
        val sentences = mutableListOf<String>()
        val matches = Regex("[^.!?]+[.!?]").findAll(cleaned)
        for (m in matches) {
            val s = m.value.trim()
            if (s.isNotEmpty()) {
                sentences.add(s)
            }
        }
        if (sentences.isEmpty() && cleaned.isNotEmpty()) {
            sentences.add(cleaned)
        }
        return sentences
    }

    override fun onCleared() {
        try {
            getApplication<Application>().unbindService(serviceConnection)
        } catch (e: Exception) {
            Log.e("ReaderViewModel", "Unbinding fail: ${e.message}")
        }
        super.onCleared()
    }
}
