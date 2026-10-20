package com.example

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.StorageMetrics
import com.example.ui.DeleteReason
import com.example.ui.RoomyScreen
import com.example.ui.RoomyViewModel
import com.example.ui.screens.BulkCompressScreen
import com.example.ui.screens.DuplicatesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.JunkScreen
import com.example.ui.screens.LargeFilesScreen
import com.example.ui.screens.MediaGridScreen
import com.example.ui.screens.PreviewScreen
import com.example.ui.screens.RamScreen
import com.example.ui.screens.ReviewScreen
import com.example.ui.theme.RoomyBackground
import com.example.ui.theme.RoomyDelete
import com.example.ui.theme.RoomyTheme

class MainActivity : ComponentActivity() {

    private val viewModel: RoomyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is light-only, so keep dark system bar icons even when the phone is in dark mode
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            RoomyTheme {
                RoomyAppHost(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun RoomyAppHost(viewModel: RoomyViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity

    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val systemRequest by viewModel.systemRequest.collectAsStateWithLifecycle()
    val deleteConfirmation by viewModel.deleteConfirmation.collectAsStateWithLifecycle()
    val compression by viewModel.compression.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val screenStates = rememberSaveableStateHolder()

    // 1. Runtime media permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val noneGranted = results.values.none { it }
        // When Android stops showing the dialog, rationale is false for every denied permission
        val blocked = noneGranted && activity != null &&
            results.keys.none { activity.shouldShowRequestPermissionRationale(it) }
        viewModel.onPermissionResult(permanentlyDenied = blocked)
    }
    val requestPermission = { permissionLauncher.launch(viewModel.permissionsToRequest) }

    LaunchedEffect(Unit) {
        if (viewModel.consumeAutoPrompt()) requestPermission()
    }

    // Detect permissions changed in Settings, and refresh storage numbers when returning to the app
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 2. Android Photo Picker (no permission needed)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> viewModel.addPickedMediaUris(uris) }
    val launchPhotoPicker = {
        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }

    val openAppSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        )
    }

    // 3. Android's own trash/delete/restore dialogs. The request lives in the ViewModel, so it survives rotation.
    val systemRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> viewModel.onSystemRequestResult(result.resultCode == Activity.RESULT_OK) }

    LaunchedEffect(systemRequest) {
        val request = systemRequest ?: return@LaunchedEffect
        if (viewModel.claimSystemRequestLaunch(request)) {
            systemRequestLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }
    }

    // 4. Messages with optional Undo
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = message.actionLabel,
                withDismissAction = message.actionLabel == null,
                duration = if (message.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) message.action?.invoke()
        }
    }

    // Keep the screen on while compressing so Android doesn't pause the work
    val view = LocalView.current
    DisposableEffect(compression.isRunning) {
        view.keepScreenOn = compression.isRunning
        onDispose { view.keepScreenOn = false }
    }

    BackHandler(enabled = currentScreen != RoomyScreen.HOME) {
        viewModel.navigateBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // The preview is full-bleed black, including behind the status and navigation bars
        containerColor = if (currentScreen == RoomyScreen.PREVIEW) androidx.compose.ui.graphics.Color.Black else RoomyBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Crossfade(
            targetState = currentScreen,
            animationSpec = tween(durationMillis = 150),
            label = "RoomyScreenTransition",
            modifier = Modifier.padding(innerPadding)
        ) { screen ->
            // Each screen keeps its scroll position and filters when you come back to it
            screenStates.SaveableStateProvider(screenStateKey(screen, viewModel)) {
                RoomyScreenContent(
                    screen = screen,
                    viewModel = viewModel,
                    onRequestPermission = requestPermission,
                    onOpenSettings = openAppSettings,
                    onLaunchPhotoPicker = launchPhotoPicker
                )
            }
        }
    }

    // In-app confirmation is only needed on Android 10 and older (newer versions show their own dialog)
    deleteConfirmation?.let { confirmation ->
        val replacing = confirmation.reason == DeleteReason.REPLACE_ORIGINALS
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeletion() },
            title = {
                Text(
                    text = if (replacing) "Replace originals?" else "Delete ${confirmation.items.size} files?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (replacing) {
                        "The smaller copies are already in your gallery. This permanently deletes the ${confirmation.items.size} original files."
                    } else {
                        "This permanently deletes ${StorageMetrics.formatBytes(confirmation.totalBytes)}. This Android version has no trash, so it can't be undone."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDeletion() },
                    colors = ButtonDefaults.buttonColors(containerColor = RoomyDelete)
                ) {
                    Text("Delete", color = androidx.compose.ui.graphics.Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeletion() }) { Text("Cancel") }
            }
        )
    }
}

private fun screenStateKey(screen: RoomyScreen, viewModel: RoomyViewModel): String = when (screen) {
    RoomyScreen.PREVIEW -> "PREVIEW_${viewModel.previewSession.value}"
    RoomyScreen.COLLECTION -> "COLLECTION_${viewModel.activeCollection.value}"
    else -> screen.name
}

@Composable
private fun RoomyScreenContent(
    screen: RoomyScreen,
    viewModel: RoomyViewModel,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunchPhotoPicker: () -> Unit
) {
    val back: () -> Unit = { viewModel.navigateBack() }
    val openPreview = viewModel::openPreview
    val delete: (List<com.example.model.MediaFileItem>) -> Unit = { viewModel.requestDelete(it) }
    val compress: (List<com.example.model.MediaFileItem>) -> Unit = { viewModel.openCompress(it) }

    when (screen) {
        RoomyScreen.HOME -> {
            val storageMetrics by viewModel.storageMetrics.collectAsStateWithLifecycle()
            val ramStats by viewModel.ramStats.collectAsStateWithLifecycle()
            val photos by viewModel.photos.collectAsStateWithLifecycle()
            val videos by viewModel.videos.collectAsStateWithLifecycle()
            val duplicateGroups by viewModel.duplicateGroups.collectAsStateWithLifecycle()
            val threshold by viewModel.largeFileThresholdMb.collectAsStateWithLifecycle()
            val mediaAccess by viewModel.mediaAccess.collectAsStateWithLifecycle()
            val permanentlyDenied by viewModel.permissionPermanentlyDenied.collectAsStateWithLifecycle()
            val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
            val scanText by viewModel.scanProgressText.collectAsStateWithLifecycle()
            val hasScanned by viewModel.hasScanned.collectAsStateWithLifecycle()
            val duplicatesChecked by viewModel.duplicatesChecked.collectAsStateWithLifecycle()
            val totalCleaned by viewModel.totalCleaned.collectAsStateWithLifecycle()
            HomeScreen(
                storageMetrics = storageMetrics,
                ramStats = ramStats,
                photos = photos,
                videos = videos,
                duplicateGroups = duplicateGroups,
                largeFileThresholdMb = threshold,
                mediaAccess = mediaAccess,
                permissionPermanentlyDenied = permanentlyDenied,
                isScanning = isScanning,
                scanProgressText = scanText,
                hasScanned = hasScanned,
                duplicatesChecked = duplicatesChecked,
                totalCleaned = totalCleaned,
                onRequestPermission = onRequestPermission,
                onOpenSettings = onOpenSettings,
                onLaunchPhotoPicker = onLaunchPhotoPicker,
                onNavigate = { viewModel.navigate(it) },
                onOpenBulkCompress = { viewModel.openCompress(null) },
                onRescan = { viewModel.scanInventory() }
            )
        }

        RoomyScreen.REVIEW -> {
            val allMedia by viewModel.allMedia.collectAsStateWithLifecycle()
            ReviewScreen(
                mediaList = allMedia,
                onKeep = { viewModel.keepItem(it) },
                onUnkeep = { viewModel.unkeepItem(it) },
                onCompress = { compress(listOf(it)) },
                onDelete = { delete(listOf(it)) },
                onOpen = openPreview,
                onBack = back
            )
        }

        RoomyScreen.PHOTOS -> {
            val photos by viewModel.photos.collectAsStateWithLifecycle()
            MediaGridScreen(
                title = "Photos",
                items = photos,
                onOpen = openPreview,
                onCompress = compress,
                onDelete = delete,
                onBack = back,
                emptyTitle = "No photos found",
                testTag = "photos_screen"
            )
        }

        RoomyScreen.VIDEOS -> {
            val videos by viewModel.videos.collectAsStateWithLifecycle()
            MediaGridScreen(
                title = "Videos",
                items = videos,
                onOpen = openPreview,
                onCompress = compress,
                onDelete = delete,
                onBack = back,
                sizeFiltersMb = listOf(50, 100, 500),
                emptyTitle = "No videos found",
                testTag = "videos_screen"
            )
        }

        RoomyScreen.COLLECTION -> {
            val collection by viewModel.activeCollection.collectAsStateWithLifecycle()
            val collections by viewModel.collections.collectAsStateWithLifecycle()
            MediaGridScreen(
                title = collection.title,
                subtitle = collection.description,
                items = collections[collection].orEmpty(),
                onOpen = openPreview,
                onCompress = compress,
                onDelete = delete,
                onBack = back,
                emptyTitle = "No ${collection.title.lowercase()} found",
                testTag = "collection_screen"
            )
        }

        RoomyScreen.LARGE_FILES -> {
            val allMedia by viewModel.allMedia.collectAsStateWithLifecycle()
            val threshold by viewModel.largeFileThresholdMb.collectAsStateWithLifecycle()
            LargeFilesScreen(
                mediaList = allMedia,
                thresholdMb = threshold,
                onSetThresholdMb = { viewModel.setLargeFileThresholdMb(it) },
                onCompress = { compress(listOf(it)) },
                onDelete = { delete(listOf(it)) },
                onOpen = openPreview,
                onBack = back
            )
        }

        RoomyScreen.DUPLICATES -> {
            val groups by viewModel.duplicateGroups.collectAsStateWithLifecycle()
            val checked by viewModel.duplicatesChecked.collectAsStateWithLifecycle()
            val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
            DuplicatesScreen(
                groups = groups,
                onToggleSelection = { grp, id -> viewModel.toggleDuplicateSelection(grp, id) },
                onDeleteSelected = { viewModel.deleteSelectedDuplicates() },
                onBack = back,
                isChecking = !checked || (isScanning && groups.isEmpty()),
                onSelectExtraCopies = { viewModel.selectExtraDuplicateCopies() },
                onClearSelection = { viewModel.clearDuplicateSelection() },
                onOpen = openPreview
            )
        }

        RoomyScreen.BULK_COMPRESS -> {
            val state by viewModel.compression.collectAsStateWithLifecycle()
            val preset by viewModel.compressionPreset.collectAsStateWithLifecycle()
            val targets by viewModel.compressTargets.collectAsStateWithLifecycle()
            val candidates by viewModel.bulkCandidates.collectAsStateWithLifecycle()
            val options by viewModel.bulkOptions.collectAsStateWithLifecycle()
            BulkCompressScreen(
                state = state,
                selectedPreset = preset,
                targets = targets,
                bulkCandidates = candidates,
                bulkOptions = options,
                onSelectPreset = { viewModel.setCompressionPreset(it) },
                onBulkOptionsChange = { viewModel.setBulkOptions(it) },
                onStartCompression = { viewModel.startCompression(it) },
                onCancel = { viewModel.cancelCompression() },
                onReplaceOriginals = { viewModel.replaceOriginals() },
                onDismissResults = { viewModel.dismissCompressionResults() },
                onBack = back
            )
        }

        RoomyScreen.JUNK -> {
            val junkItems by viewModel.junkItems.collectAsStateWithLifecycle()
            val collections by viewModel.collections.collectAsStateWithLifecycle()
            JunkScreen(
                junkItems = junkItems,
                onToggleJunk = { viewModel.toggleJunkSelection(it) },
                onDeleteSelected = { viewModel.deleteSelectedJunk() },
                onBack = back,
                collections = collections,
                onOpenCollection = { viewModel.openCollection(it) }
            )
        }

        RoomyScreen.RAM -> {
            val ramStats by viewModel.ramStats.collectAsStateWithLifecycle()
            RamScreen(ramStats = ramStats, onBack = back)
        }

        RoomyScreen.PREVIEW -> {
            val items by viewModel.previewItems.collectAsStateWithLifecycle()
            val start by viewModel.previewStartIndex.collectAsStateWithLifecycle()
            PreviewScreen(
                items = items,
                startIndex = start,
                onBack = back,
                onToggleKeep = { if (it.isExcluded) viewModel.unkeepItem(it) else viewModel.keepItem(it) },
                onCompress = { compress(listOf(it)) },
                onDelete = { delete(listOf(it)) }
            )
        }
    }
}
