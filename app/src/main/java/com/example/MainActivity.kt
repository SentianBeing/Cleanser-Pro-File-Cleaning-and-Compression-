package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.MediaFileItem
import com.example.ui.RoomyScreen
import com.example.ui.RoomyViewModel
import com.example.ui.screens.BulkCompressScreen
import com.example.ui.screens.DuplicatesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.JunkScreen
import com.example.ui.screens.LargeFilesScreen
import com.example.ui.screens.PhotosScreen
import com.example.ui.screens.RamScreen
import com.example.ui.screens.ReviewScreen
import com.example.ui.screens.VideosScreen
import com.example.ui.theme.RoomyTheme

class MainActivity : ComponentActivity() {

    private val viewModel: RoomyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RoomyTheme {
                RoomyAppHost(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun RoomyAppHost(viewModel: RoomyViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val storageMetrics by viewModel.storageMetrics.collectAsStateWithLifecycle()
    val ramStats by viewModel.ramStats.collectAsStateWithLifecycle()
    val allMedia by viewModel.allMedia.collectAsStateWithLifecycle()
    val duplicateGroups by viewModel.duplicateGroups.collectAsStateWithLifecycle()
    val junkItems by viewModel.junkItems.collectAsStateWithLifecycle()
    val largeFileThresholdMb by viewModel.largeFileThresholdMb.collectAsStateWithLifecycle()

    val compressionPreset by viewModel.compressionPreset.collectAsStateWithLifecycle()
    val isCompressing by viewModel.isCompressing.collectAsStateWithLifecycle()
    val compressionProgress by viewModel.compressionProgress.collectAsStateWithLifecycle()
    val compressionResults by viewModel.compressionResults.collectAsStateWithLifecycle()

    // Handle system back navigation to return to Home
    BackHandler(enabled = currentScreen != RoomyScreen.HOME) {
        viewModel.navigateBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Crossfade(
            targetState = currentScreen,
            label = "RoomyScreenTransition",
            modifier = Modifier.padding(innerPadding)
        ) { screen ->
            when (screen) {
                RoomyScreen.HOME -> HomeScreen(
                    storageMetrics = storageMetrics,
                    ramStats = ramStats,
                    mediaList = allMedia,
                    duplicateGroups = duplicateGroups,
                    onNavigate = { viewModel.navigate(it) },
                    onRescan = { viewModel.scanInventory() }
                )

                RoomyScreen.REVIEW -> ReviewScreen(
                    mediaList = allMedia,
                    onKeep = { viewModel.keepItem(it) },
                    onUnkeep = { viewModel.unkeepItem(it) },
                    onCompress = {
                        viewModel.compressSingleItem(it)
                        viewModel.navigate(RoomyScreen.BULK_COMPRESS)
                    },
                    onDelete = { viewModel.deleteItem(it) },
                    onBack = { viewModel.navigateBack() }
                )

                RoomyScreen.PHOTOS -> PhotosScreen(
                    photosList = allMedia.filter { !it.isVideo },
                    onCompressPhoto = {
                        viewModel.compressSingleItem(it)
                        viewModel.navigate(RoomyScreen.BULK_COMPRESS)
                    },
                    onDeletePhoto = { viewModel.deleteItem(it) },
                    onBack = { viewModel.navigateBack() }
                )

                RoomyScreen.VIDEOS -> VideosScreen(
                    videosList = allMedia.filter { it.isVideo },
                    onCompressVideo = {
                        viewModel.compressSingleItem(it)
                        viewModel.navigate(RoomyScreen.BULK_COMPRESS)
                    },
                    onDeleteVideo = { viewModel.deleteItem(it) },
                    onBack = { viewModel.navigateBack() }
                )

                RoomyScreen.LARGE_FILES -> LargeFilesScreen(
                    mediaList = allMedia,
                    thresholdMb = largeFileThresholdMb,
                    onSetThresholdMb = { viewModel.setLargeFileThresholdMb(it) },
                    onCompress = {
                        viewModel.compressSingleItem(it)
                        viewModel.navigate(RoomyScreen.BULK_COMPRESS)
                    },
                    onDelete = { viewModel.deleteItem(it) },
                    onBack = { viewModel.navigateBack() }
                )

                RoomyScreen.DUPLICATES -> DuplicatesScreen(
                    groups = duplicateGroups,
                    onToggleSelection = { grp, id -> viewModel.toggleDuplicateSelection(grp, id) },
                    onDeleteSelected = { viewModel.deleteSelectedDuplicates() },
                    onBack = { viewModel.navigateBack() }
                )

                RoomyScreen.BULK_COMPRESS -> BulkCompressScreen(
                    mediaList = allMedia,
                    selectedPreset = compressionPreset,
                    isCompressing = isCompressing,
                    progress = compressionProgress,
                    results = compressionResults,
                    onSelectPreset = { viewModel.setCompressionPreset(it) },
                    onStartCompression = { viewModel.compressBatch(it) },
                    onDismissResults = { viewModel.dismissCompressionResults() },
                    onBack = { viewModel.navigateBack() }
                )

                RoomyScreen.JUNK -> JunkScreen(
                    junkItems = junkItems,
                    onToggleJunk = { viewModel.toggleJunkSelection(it) },
                    onDeleteSelected = { viewModel.deleteSelectedJunk() },
                    onBack = { viewModel.navigateBack() }
                )

                RoomyScreen.RAM -> RamScreen(
                    ramStats = ramStats,
                    onBack = { viewModel.navigateBack() }
                )
            }
        }
    }
}
