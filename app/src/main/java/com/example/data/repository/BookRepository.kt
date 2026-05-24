package com.example.data.repository

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.data.local.BookDao
import com.example.data.local.FolderDao
import com.example.data.model.Book
import com.example.data.model.DriveFolder
import com.example.data.remote.DriveApiService
import com.example.data.remote.DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class BookRepository(
    private val context: Context,
    private val bookDao: BookDao,
    private val folderDao: FolderDao,
    private val driveApiService: DriveApiService
) {
    val allBooks: Flow<List<Book>> = bookDao.getAllBooks()
    val allFolders: Flow<List<DriveFolder>> = folderDao.getAllFolders()

    fun getBooksByFolder(folderId: String): Flow<List<Book>> {
        return bookDao.getBooksByFolder(folderId)
    }

    fun getBookById(id: String): Flow<Book?> {
        return bookDao.getBookById(id)
    }

    suspend fun saveFolder(id: String, name: String) = withContext(Dispatchers.IO) {
        folderDao.insertFolder(DriveFolder(id, name))
    }

    suspend fun deleteFolder(folder: DriveFolder) = withContext(Dispatchers.IO) {
        folderDao.deleteFolder(folder)
    }

    suspend fun updateBookProgress(id: String, page: Int) = withContext(Dispatchers.IO) {
        val book = bookDao.getBookSync(id)
        if (book != null) {
            bookDao.updateBook(book.copy(currentPage = page))
        }
    }

    suspend fun listFoldersFromDrive(accessToken: String): List<DriveFile> = withContext(Dispatchers.IO) {
        val query = "mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val response = driveApiService.listFiles("Bearer $accessToken", query)
        response.files
    }

    suspend fun listPdfFilesFromDrive(accessToken: String, folderId: String): List<DriveFile> = withContext(Dispatchers.IO) {
        val query = "'$folderId' in parents and mimeType = 'application/pdf' and trashed = false"
        val response = driveApiService.listFiles("Bearer $accessToken", query)
        response.files
    }

    suspend fun importDrivePdf(driveFile: DriveFile, folderId: String) = withContext(Dispatchers.IO) {
        val existing = bookDao.getBookSync(driveFile.id)
        if (existing == null) {
            val book = Book(
                id = driveFile.id,
                title = driveFile.name,
                folderId = folderId,
                isCached = false,
                downloadProgress = 0f
            )
            bookDao.insertBook(book)
        }
    }

    suspend fun downloadBookPdf(accessToken: String, bookId: String) = withContext(Dispatchers.IO) {
        val book = bookDao.getBookSync(bookId) ?: return@withContext
        bookDao.updateBook(book.copy(downloadProgress = 0.1f))

        try {
            val response = driveApiService.downloadFile("Bearer $accessToken", bookId)
            if (!response.isSuccessful) {
                Log.e("BookRepository", "Failed to download book: ${response.errorBody()?.string()}")
                bookDao.updateBook(book.copy(downloadProgress = 0f))
                return@withContext
            }

            val body = response.body()
            if (body == null) {
                bookDao.updateBook(book.copy(downloadProgress = 0f))
                return@withContext
            }

            // Ensure cached folder exists
            val booksDir = File(context.filesDir, "books")
            if (!booksDir.exists()) {
                booksDir.mkdirs()
            }

            val outputFile = File(booksDir, "$bookId.pdf")
            val totalBytes = body.contentLength()
            var bytesCopied: Long = 0

            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytes = input.read(buffer)
                    var updateTimer = 0L
                    while (bytes >= 0) {
                        output.write(buffer, 0, bytes)
                        bytesCopied += bytes
                        bytes = input.read(buffer)

                        // Only notify database occasionally to avoid SQLite thrashing
                        val now = System.currentTimeMillis()
                        if (now - updateTimer > 300 && totalBytes > 0) {
                            updateTimer = now
                            val progress = bytesCopied.toFloat() / totalBytes
                            bookDao.updateBook(book.copy(downloadProgress = progress))
                        }
                    }
                }
            }

            // Calculate exact total pages using native PdfRenderer
            var pages = 0
            try {
                val pfd = ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                pages = renderer.pageCount
                renderer.close()
                pfd.close()
            } catch (e: Exception) {
                Log.e("BookRepository", "Failed to open PDF and get page count: ${e.message}")
            }

            bookDao.updateBook(book.copy(
                localPath = outputFile.absolutePath,
                isCached = true,
                totalPages = pages,
                downloadProgress = 1.0f
            ))
        } catch (e: Exception) {
            Log.e("BookRepository", "Exception during download: ${e.message}")
            bookDao.updateBook(book.copy(downloadProgress = 0f))
        }
    }
}
