package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.screen.DriveScreen
import com.example.ui.screen.ReaderScreen
import com.example.ui.viewmodel.DriveViewModel
import com.example.ui.viewmodel.ReaderViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      android.util.Log.e("CRASH_LOGGER", "FATAL UNCAUGHT EXCEPTION: Thread[${thread.name}]", throwable)
      if (oldHandler != null) {
        oldHandler.uncaughtException(thread, throwable)
      } else {
        System.exit(1)
      }
    }
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val navController = rememberNavController()
        
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          NavHost(
            navController = navController,
            startDestination = "drive",
            modifier = Modifier.padding(innerPadding)
          ) {
            composable("drive") {
              val driveViewModel: DriveViewModel = viewModel()
              DriveScreen(
                viewModel = driveViewModel,
                onOpenBook = { bookId ->
                  navController.navigate("reader/$bookId")
                }
              )
            }
            composable("reader/{bookId}") { backStackEntry ->
              val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
              val readerViewModel: ReaderViewModel = viewModel()
              ReaderScreen(
                viewModel = readerViewModel,
                bookId = bookId,
                onBack = {
                  readerViewModel.stopTts()
                  navController.popBackStack()
                }
              )
            }
          }
        }
      }
    }
  }
}
