package com.example.ui.screen

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Book
import com.example.data.remote.DriveFile
import com.example.ui.viewmodel.DriveViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveScreen(
    viewModel: DriveViewModel,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val accessToken by viewModel.settingsHelper.accessToken.collectAsStateWithLifecycle()
    val selectedFolderId by viewModel.settingsHelper.selectedFolderId.collectAsStateWithLifecycle()
    val selectedFolderName by viewModel.settingsHelper.selectedFolderName.collectAsStateWithLifecycle()
    
    val localBooks by viewModel.localBooks.collectAsStateWithLifecycle(initialValue = emptyList())
    val savedFolders by viewModel.savedFolders.collectAsStateWithLifecycle(initialValue = emptyList())
    
    val foldersState by viewModel.foldersState.collectAsStateWithLifecycle()
    val filesState by viewModel.filesState.collectAsStateWithLifecycle()

    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val streamingProgress by viewModel.streamingProgress.collectAsStateWithLifecycle()

    var tokenInput by remember { mutableStateOf(accessToken) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var showGoogleChooser by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) } // 0: Local Library, 1: Cloud Explorer
    var isDownloadingSample by remember { mutableStateOf(false) }
    var sampleDownloadProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(accessToken) {
        tokenInput = accessToken
    }

    LaunchedEffect(selectedFolderId) {
        if (selectedFolderId.isNotEmpty() && accessToken.isNotEmpty()) {
            viewModel.fetchFilesForFolder(selectedFolderId)
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            "Drive Reader",
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                        )
                        Text(
                            "Google Drive PDF Audiobook Player",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showTokenDialog = true },
                        modifier = Modifier.testTag("oauth_setting_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (accessToken.isEmpty()) {
                                    Badge { Text("!") }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = "Manual OAuth Token Setup"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Tab Selector
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("Local Library", fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.MenuBook, contentDescription = null) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("Cloud Explorer", fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.CloudQueue, contentDescription = null) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (activeTab == 0) {
                    // Local Library Collection
                    if (localBooks.isEmpty() && !isDownloadingSample) {
                        EmptyLibraryState(
                            onAddSample = {
                                isDownloadingSample = true
                                scope.launch {
                                    downloadSamplePdf(
                                        context = context,
                                        onProgress = { sampleDownloadProgress = it },
                                        onComplete = { file ->
                                            isDownloadingSample = false
                                            scope.launch {
                                                viewModel.repository.importDrivePdf(
                                                    DriveFile(
                                                        id = "sample_alice",
                                                        name = "Alice in Wonderland (Sample Book).pdf",
                                                        mimeType = "application/pdf"
                                                    ),
                                                    folderId = "local"
                                                )
                                                viewModel.repository.downloadBookPdf("demo", "sample_alice")
                                            }
                                        },
                                        onError = {
                                            isDownloadingSample = false
                                        }
                                    )
                                }
                            }
                        )
                    } else if (isDownloadingSample) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(16.dp)
                               )
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(progress = { sampleDownloadProgress })
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Downloading Classic Sample Book...",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Retrieving rich formatting and sentence structures...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            item {
                                Text(
                                    "Your Offline Books",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            
                            items(localBooks) { book ->
                                BookLibraryCard(
                                    book = book,
                                    onClick = {
                                        if (book.isCached) {
                                            onOpenBook(book.id)
                                        } else {
                                            viewModel.downloadBook(book.id)
                                        }
                                    },
                                    onDownload = {
                                        viewModel.downloadBook(book.id)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Cloud Google Drive Explorer
                    if (accessToken.isEmpty()) {
                        CloudUnauthorizedState(
                            onConfigure = { showTokenDialog = true },
                            onGoogleSignIn = { showGoogleChooser = true }
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Google Account login Banner
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(16.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        userEmail.take(1).uppercase(),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Signed In as Google User",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        userEmail.ifEmpty { "ramazanenes1983@gmail.com" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(
                                    onClick = { viewModel.clearSession() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Disconnect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Quick Directory picker header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Google Drive Folder",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        selectedFolderName.ifEmpty { "Select a folder from Drive..." },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Button(
                                    onClick = {
                                        viewModel.fetchDriveFolders()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Browse", fontSize = 12.sp)
                                }
                            }

                            // Display fetched folders inside list if browsing
                            when (val folders = foldersState) {
                                is DriveViewModel.UiState.Loading -> {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                                is DriveViewModel.UiState.Success -> {
                                    SectionHeader(title = "Select Google Drive Folder")
                                    LazyColumn(
                                        modifier = Modifier
                                            .weight(0.4f)
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(folders.data) { folder ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        MaterialTheme.colorScheme.surface,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable {
                                                        viewModel.selectFolder(folder.id, folder.name)
                                                    }
                                                    .padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFFC107)
                                                )
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Text(
                                                    folder.name,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                                is DriveViewModel.UiState.Error -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            folders.message,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                else -> {}
                            }

                            // Display files inside the current folder
                            if (selectedFolderId.isNotEmpty()) {
                                SectionHeader(title = "PDF Books in Folder")
                                when (val files = filesState) {
                                    is DriveViewModel.UiState.Loading -> {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                    is DriveViewModel.UiState.Success -> {
                                        if (files.data.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxWidth(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("No PDF files found in this folder.")
                                            }
                                        } else {
                                            LazyColumn(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                                contentPadding = PaddingValues(bottom = 24.dp)
                                            ) {
                                                items(files.data) { file ->
                                                    val isImported = localBooks.any { it.id == file.id }
                                                    val importedBook = localBooks.find { it.id == file.id }
                                                    
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(
                                                                MaterialTheme.colorScheme.surface,
                                                                shape = RoundedCornerShape(10.dp)
                                                            )
                                                            .padding(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            Icons.Default.PictureAsPdf,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(36.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(14.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                file.name,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 14.sp,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            if (importedBook != null) {
                                                                if (importedBook.isCached) {
                                                                    Text(
                                                                        "Downloaded (Local Library)",
                                                                        color = MaterialTheme.colorScheme.secondary,
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                } else if (importedBook.downloadProgress > 0) {
                                                                    LinearProgressIndicator(
                                                                        progress = { importedBook.downloadProgress },
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .padding(top = 4.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        
                                                        // ACTION PACK: ONLINE VS OFFLINE
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            // Theme 1: STREAM ONLINE CHIP (ONLINE MODE)
                                                            IconButton(
                                                                onClick = {
                                                                    viewModel.streamBookOnline(file) { bookId ->
                                                                        onOpenBook(bookId)
                                                                    }
                                                                },
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.PlayArrow,
                                                                    contentDescription = "Stream Book in Online Cache Mode",
                                                                    tint = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.width(8.dp))

                                                            // Theme 2: OFFLINE SYSTEM DOWNLOAD
                                                            if (!isImported) {
                                                                IconButton(
                                                                    onClick = {
                                                                        viewModel.importPdfFile(file, selectedFolderId)
                                                                    }
                                                                ) {
                                                                    Icon(
                                                                        Icons.Default.AddCircle,
                                                                        contentDescription = "Import metadata to library",
                                                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                                    )
                                                                }
                                                            } else if (importedBook != null && !importedBook.isCached && importedBook.downloadProgress == 0f) {
                                                                IconButton(
                                                                    onClick = {
                                                                        viewModel.downloadBook(file.id)
                                                                    }
                                                                ) {
                                                                    Icon(
                                                                        Icons.Default.Download,
                                                                        contentDescription = "Download to local library securely",
                                                                        tint = MaterialTheme.colorScheme.primary
                                                                    )
                                                                }
                                                            } else if (importedBook?.isCached == true) {
                                                                IconButton(
                                                                    onClick = {
                                                                        onOpenBook(file.id)
                                                                    }
                                                                ) {
                                                                    Icon(
                                                                        Icons.Default.ArrowForward,
                                                                        contentDescription = "Read local copy",
                                                                        tint = MaterialTheme.colorScheme.secondary
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    is DriveViewModel.UiState.Error -> {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                files.message,
                                                color = MaterialTheme.colorScheme.error,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                modifier = Modifier.padding(16.dp)
                                            )
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }
            }

            // Online Mode Temp Cache Streaming Overlay
            if (streamingProgress != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Streaming Online Book...",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Downloading securely to temporary memory cache only. Not indexed to persistent local library storage.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { streamingProgress ?: 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
            }
        }
    }

    // Official Google Sign-In Account Chooser Simulation Dialog
    if (showGoogleChooser) {
        AlertDialog(
            onDismissRequest = { showGoogleChooser = false },
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Colorful Google letters
                    Row {
                        Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Text("o", color = Color(0xFFEA4335), fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Text("o", color = Color(0xFFFBBC05), fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Text("g", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Text("l", color = Color(0xFF34A853), fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Text("e", color = Color(0xFFEA4335), fontWeight = FontWeight.Black, fontSize = 22.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Choose an account",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "to continue to DriveReader app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            },
            text = {
                Column {
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    // Chosen logged in profile
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Fast sign-in setup with standard auth session details
                                viewModel.googleSignIn("ramazanenes1983@gmail.com", "mock_oauth_bearer_active_token_xyz")
                                showGoogleChooser = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFE8F0FE), shape = RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("R", color = Color(0xFF1967D2), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Ramazan Enes", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("ramazanenes1983@gmail.com", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showGoogleChooser = false
                                showTokenDialog = true
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            "Use developer manual Bearer",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showGoogleChooser = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Token configuration Dialog
    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("Configure Google Drive Access") },
            text = {
                Column {
                    Text(
                        "Paste a Google Drive OAuth Bearer token to connect to your live directories.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("OAuth Access Token Bearer") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("oauth_token_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "If you do not have GCP configurations completed yet, use the 'Sign In with Google' button in Cloud Explorer to mock a live login or click 'Download Classic Sample Book' to view offline TTS immediately!",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveToken(tokenInput)
                        showTokenDialog = false
                    }
                ) {
                    Text("Apply Token")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun BookLibraryCard(
    book: Book,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("book_card_${book.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                            )
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Book,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    book.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (book.isCached) {
                    Text(
                        "Progress: Page ${book.currentPage + 1} of ${book.totalPages}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else if (book.downloadProgress > 0) {
                    Column {
                        Text(
                            "Downloading: ${(book.downloadProgress * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LinearProgressIndicator(
                            progress = { book.downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }
                } else {
                    Text(
                        "Click to retrieve local book copy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (!book.isCached && book.downloadProgress == 0f) {
                IconButton(onClick = onDownload) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download"
                    )
                }
            } else if (book.isCached) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Read",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyLibraryState(
    onAddSample: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .background(
                MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.LibraryBooks,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Library is Empty",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Select PDFs from Google Drive in Cloud Explorer or try with an off-the-shelf classic public domain book to run immediately.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAddSample,
            modifier = Modifier.testTag("sample_book_button")
        ) {
            Icon(Icons.Default.DownloadForOffline, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Download Classic Sample Book")
        }
    }
}

@Composable
fun CloudUnauthorizedState(
    onConfigure: () -> Unit,
    onGoogleSignIn: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .background(
                MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CloudQueue,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Authentication Required",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Securely log in to explore folders, list documents, and stream books on demand from Google Drive.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Stylish dynamic Google Sign-In Button
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable(onClick = onGoogleSignIn)
                .testTag("google_signin_button"),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(26.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Vibrant multi-color G-o-o-g-l-e title text
                Row(modifier = Modifier.padding(end = 12.dp)) {
                    Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text("o", color = Color(0xFFEA4335), fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text("o", color = Color(0xFFFBBC05), fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text("g", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text("l", color = Color(0xFF34A853), fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text("e", color = Color(0xFFEA4335), fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
                Text(
                    "Sign In with Google",
                    color = Color(0xFF1F1F1F),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        
        TextButton(onClick = onConfigure) {
            Text("Or enter manual developer bearer token", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

suspend fun downloadSamplePdf(
    context: Context,
    onProgress: (Float) -> Unit,
    onComplete: (File) -> Unit,
    onError: (Throwable) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf")
        val connection = url.openConnection()
        connection.connect()

        val length = connection.contentLength
        val booksDir = File(context.filesDir, "books")
        if (!booksDir.exists()) {
            booksDir.mkdirs()
        }
        val outputFile = File(booksDir, "sample_alice.pdf")

        connection.getInputStream().use { input ->
            FileOutputStream(outputFile).use { output ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                var totalRead = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (length > 0) {
                        withContext(Dispatchers.Main) {
                            onProgress(totalRead.toFloat() / length)
                        }
                    }
                }
            }
        }
        withContext(Dispatchers.Main) {
            onComplete(outputFile)
        }
    } catch (e: Throwable) {
        withContext(Dispatchers.Main) {
            onError(e)
        }
    }
}
