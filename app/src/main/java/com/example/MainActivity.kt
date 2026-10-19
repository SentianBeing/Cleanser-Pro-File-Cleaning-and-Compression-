package com.example

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val hasMediaPermission by viewModel.hasMediaPermission.collectAsStateWithLifecycle()

    val compressionPreset by viewModel.compressionPreset.collectAsStateWithLifecycle()
    val isCompressing by viewModel.isCompressing.collectAsStateWithLifecycle()
    val compressionProgress by viewModel.compressionProgress.collectAsStateWithLifecycle()
    val compressionResults by viewModel.compressionResults.collectAsStateWithLifecycle()

    // 1. Android Runtime Media Permissions Launcher
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        viewModel.updatePermissionState(granted)
    }

    // Auto-prompt media permission on first launch if not yet granted
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!hasMediaPermission) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    // Listen to lifecycle ON_RESUME so permissions granted in Android Settings are immediately detected
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissionsAndRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 2. Android Photo Picker Launcher (Fallback / Zero-Permission)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addPickedMediaUris(uris)
        }
    }

    // 3. Official Android OS System Deletion Consent Launcher (MediaStore.createDeleteRequest)
    var pendingDeletionItems by remember { mutableStateOf<List<MediaFileItem>>(emptyList()) }

    val systemDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            viewModel.onDeletionCompleted(pendingDeletionItems)
        }
        pendingDeletionItems = emptyList()
    }

    fun initiateSystemDeletion(items: List<MediaFileItem>) {
        if (items.isEmpty()) return
        pendingDeletionItems = items
        val intentSender = viewModel.createDeleteIntentSender(items)
        if (intentSender != null) {
            val request = IntentSenderRequest.Builder(intentSender).build()
            systemDeleteLauncher.launch(request)
        } else {
            // Direct deletion for app-owned files or older API
            viewModel.onDeletionCompleted(items)
            pendingDeletionItems = emptyList()
        }
    }

    // System back navigation
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
                    hasMediaPermission = hasMediaPermission,
                    onRequestPermission = { permissionLauncher.launch(permissionsToRequest) },
                    onLaunchPhotoPicker = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
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
                    onDelete = { initiateSystemDeletion(listOf(it)) },
                    onBack = { viewModel.navigateBack() }
                )

                RoomyScreen.PHOTOS -> PhotosScreen(
                    photosList = allMedia.filter { !it.isVideo },
                    onCompressPhoto = {
                        viewModel.compressSingleItem(it)
                        viewModel.navigate(RoomyScreen.BULK_COMPRESS)
                    },
                    onDeletePhoto = { initiateSystemDeletion(listOf(it)) },
                    onBack = { viewModel.navigateBack() }
                )

                RoomyScreen.VIDEOS -> VideosScreen(
                    videosList = allMedia.filter { it.isVideo },
                    onCompressVideo = {
                        viewModel.compressSingleItem(it)
                        viewModel.navigate(RoomyScreen.BULK_COMPRESS)
                    },
                    onDeleteVideo = { initiateSystemDeletion(listOf(it)) },
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
                    onDelete = { initiateSystemDeletion(listOf(it)) },
                    onBack = { viewModel.navigateBack() }
                )

                RoomyScreen.DUPLICATES -> DuplicatesScreen(
                    groups = duplicateGroups,
                    onToggleSelection = { grp, id -> viewModel.toggleDuplicateSelection(grp, id) },
                    onDeleteSelected = {
                        val selected = viewModel.getSelectedDuplicates()
                        initiateSystemDeletion(selected)
                    },
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
