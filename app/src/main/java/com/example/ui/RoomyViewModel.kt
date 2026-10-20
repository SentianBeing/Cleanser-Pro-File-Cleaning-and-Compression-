package com.example.ui

import android.Manifest
import android.app.Application
import android.content.IntentSender
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DirectDeleteResult
import com.example.data.MediaRequestKind
import com.example.data.RoomyRepository
import com.example.model.CompressionJobResult
import com.example.model.CompressionPreset
import com.example.model.DuplicateGroup
import com.example.model.JunkItem
import com.example.model.MediaCollection
import com.example.model.MediaFileItem
import com.example.model.RamStats
import com.example.model.ReviewAction
import com.example.model.StorageMetrics
import com.example.model.recommendKeeper
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

enum class MediaAccess { FULL, PARTIAL, NONE }

enum class DeleteReason { USER, DUPLICATES, REPLACE_ORIGINALS }

/** An Android system dialog (trash, delete, restore, or Android 10's per-file consent) waiting to be shown. */
data class SystemRequest(
    val id: Long,
    val intentSender: IntentSender,
    val kind: Kind,
    val items: List<MediaFileItem>,
    val reason: DeleteReason
) {
    enum class Kind { TRASH, DELETE, RESTORE, LEGACY_CONSENT }
}

/** In-app confirmation, only used on Android 10 and older where there is no system dialog. */
data class DeleteConfirmation(val items: List<MediaFileItem>, val reason: DeleteReason) {
    val totalBytes: Long get() = items.sumOf { it.sizeBytes }
}

class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

data class CompressionState(
    val isRunning: Boolean = false,
    val total: Int = 0,
    val done: Int = 0,
    val currentName: String = "",
    val currentProgress: Float = 0f,
    val results: List<CompressionJobResult> = emptyList(),
    val wasCancelled: Boolean = false,
    val originalsReplaced: Boolean = false
) {
    val overallProgress: Float get() = if (total == 0) 0f else ((done + currentProgress) / total).coerceIn(0f, 1f)
    val savedResults: List<CompressionJobResult> get() = results.filter { it.isUsefulReduction }
    val potentialSavings: Long get() = savedResults.sumOf { it.savedBytes }
}

data class BulkCompressOptions(
    val includePhotos: Boolean = true,
    val includeVideos: Boolean = false,
    val minSizeMb: Int = 1
)

class RoomyViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val repository = RoomyRepository(application)

    // -------------------------------------------------------------
    // Navigation (simple back stack)
    // -------------------------------------------------------------

    private val backStack = mutableListOf(RoomyScreen.HOME)
    private val _currentScreen = MutableStateFlow(RoomyScreen.HOME)
    val currentScreen: StateFlow<RoomyScreen> = _currentScreen.asStateFlow()

    // -------------------------------------------------------------
    // Device & media state
    // -------------------------------------------------------------

    private val _storageMetrics = MutableStateFlow(StorageMetrics())
    val storageMetrics: StateFlow<StorageMetrics> = _storageMetrics.asStateFlow()

    private val _ramStats = MutableStateFlow(RamStats())
    val ramStats: StateFlow<RamStats> = _ramStats.asStateFlow()

    private val _allMedia = MutableStateFlow<List<MediaFileItem>>(emptyList())
    val allMedia: StateFlow<List<MediaFileItem>> = _allMedia.asStateFlow()

    // Derived lists are computed once per change, off the main thread, instead of on every redraw
    val photos: StateFlow<List<MediaFileItem>> = derived { list -> list.filter { !it.isVideo } }
    val videos: StateFlow<List<MediaFileItem>> = derived { list -> list.filter { it.isVideo } }
    val collections: StateFlow<Map<MediaCollection, List<MediaFileItem>>> =
        derived(emptyMap()) { list -> MediaCollection.entries.associateWith { c -> list.filter { c.matches(it) } } }
    private val mediaById: StateFlow<Map<String, MediaFileItem>> = derived(emptyMap()) { list -> list.associateBy { it.id } }

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgressText = MutableStateFlow("")
    val scanProgressText: StateFlow<String> = _scanProgressText.asStateFlow()

    private val _hasScanned = MutableStateFlow(false)
    val hasScanned: StateFlow<Boolean> = _hasScanned.asStateFlow()

    private val _totalCleaned = MutableStateFlow(0L)
    val totalCleaned: StateFlow<Long> = _totalCleaned.asStateFlow()

    private val keptIds = HashSet<String>()
    private val compressedCopyIds = HashSet<String>()

    private var scanJob: Job? = null
    private var rescanDebounce: Job? = null
    private var rescanWhenIdle = false

    // -------------------------------------------------------------
    // Permissions
    // -------------------------------------------------------------

    private val _mediaAccess = MutableStateFlow(readMediaAccess())
    val mediaAccess: StateFlow<MediaAccess> = _mediaAccess.asStateFlow()

    private val _permissionPermanentlyDenied = MutableStateFlow(false)
    val permissionPermanentlyDenied: StateFlow<Boolean> = _permissionPermanentlyDenied.asStateFlow()

    private var autoPromptDone = false

    val permissionsToRequest: Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    // -------------------------------------------------------------
    // Duplicates
    // -------------------------------------------------------------

    private val _rawDuplicateGroups = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    private val _duplicateSelection = MutableStateFlow<Set<String>>(emptySet())
    val duplicateGroups: StateFlow<List<DuplicateGroup>> =
        combine(_rawDuplicateGroups, _duplicateSelection) { groups, selected ->
            if (selected.isEmpty()) groups else groups.map { g ->
                g.copy(files = g.files.map { f -> f.copy(isSelectedForDelete = f.id in selected) })
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _duplicatesChecked = MutableStateFlow(false)
    val duplicatesChecked: StateFlow<Boolean> = _duplicatesChecked.asStateFlow()

    // -------------------------------------------------------------
    // Junk, large files
    // -------------------------------------------------------------

    private val _junkItems = MutableStateFlow<List<JunkItem>>(emptyList())
    val junkItems: StateFlow<List<JunkItem>> = _junkItems.asStateFlow()

    private val _largeFileThresholdMb = MutableStateFlow(100) // Default 100 MB per PRD
    val largeFileThresholdMb: StateFlow<Int> = _largeFileThresholdMb.asStateFlow()

    // -------------------------------------------------------------
    // Preview & collections
    // -------------------------------------------------------------

    private val _previewIds = MutableStateFlow<List<String>>(emptyList())
    val previewItems: StateFlow<List<MediaFileItem>> =
        combine(_previewIds, mediaById) { ids, byId -> ids.mapNotNull { byId[it] } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _previewStartIndex = MutableStateFlow(0)
    val previewStartIndex: StateFlow<Int> = _previewStartIndex.asStateFlow()

    private val _previewSession = MutableStateFlow(0)
    val previewSession: StateFlow<Int> = _previewSession.asStateFlow()

    private val _activeCollection = MutableStateFlow(MediaCollection.SCREENSHOTS)
    val activeCollection: StateFlow<MediaCollection> = _activeCollection.asStateFlow()

    // -------------------------------------------------------------
    // Compression
    // -------------------------------------------------------------

    // High quality by default: shrinking should never noticeably change what people keep
    private val _compressionPreset = MutableStateFlow(CompressionPreset.HIGH_VISUAL_QUALITY)
    val compressionPreset: StateFlow<CompressionPreset> = _compressionPreset.asStateFlow()

    private val _compression = MutableStateFlow(CompressionState())
    val compression: StateFlow<CompressionState> = _compression.asStateFlow()

    private val _compressTargetIds = MutableStateFlow<List<String>?>(null) // null = bulk mode
    val compressTargets: StateFlow<List<MediaFileItem>?> =
        combine(_compressTargetIds, mediaById) { ids, byId -> ids?.mapNotNull { byId[it] } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _bulkOptions = MutableStateFlow(BulkCompressOptions())
    val bulkOptions: StateFlow<BulkCompressOptions> = _bulkOptions.asStateFlow()

    val bulkCandidates: StateFlow<List<MediaFileItem>> =
        combine(_allMedia, _bulkOptions) { list, options ->
            val minBytes = options.minSizeMb * 1_000_000L
            list.filter {
                isCompressible(it) && it.sizeBytes >= minBytes &&
                    ((it.isVideo && options.includeVideos) || (!it.isVideo && options.includePhotos))
            }
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var compressionJob: Job? = null

    // -------------------------------------------------------------
    // Deleting
    // -------------------------------------------------------------

    private val _systemRequest = MutableStateFlow<SystemRequest?>(null)
    val systemRequest: StateFlow<SystemRequest?> = _systemRequest.asStateFlow()
    private var nextRequestId = 1L
    private var launchedRequestId = 0L

    private val _deleteConfirmation = MutableStateFlow<DeleteConfirmation?>(null)
    val deleteConfirmation: StateFlow<DeleteConfirmation?> = _deleteConfirmation.asStateFlow()

    // Android 10 and older delete one file at a time and may need a consent dialog per file
    private val legacyQueue = ArrayDeque<MediaFileItem>()
    private var legacyRequested: List<MediaFileItem> = emptyList()
    private var legacyReason = DeleteReason.USER
    private val legacyConsented = HashSet<String>()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    // Rescan automatically when photos are added or removed outside the app
    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = scheduleRescan()
    }

    init {
        app.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
        app.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
        refreshMetrics()
        viewModelScope.launch {
            val (kept, copies) = repository.loadFlags()
            keptIds.addAll(kept)
            compressedCopyIds.addAll(copies)
            scanInventory()
        }
        viewModelScope.launch {
            repository.totalBytesCleaned.collect { _totalCleaned.value = it ?: 0L }
        }
    }

    override fun onCleared() {
        app.contentResolver.unregisterContentObserver(mediaObserver)
    }

    private fun <T> derived(initial: T, transform: (List<MediaFileItem>) -> T): StateFlow<T> =
        _allMedia.map(transform).flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    private fun derived(transform: (List<MediaFileItem>) -> List<MediaFileItem>): StateFlow<List<MediaFileItem>> =
        derived(emptyList(), transform)

    private fun message(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        _messages.trySend(UiMessage(text, actionLabel, action))
    }

    // -------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------

    fun navigate(screen: RoomyScreen) {
        // Going to a screen already in the stack returns to it instead of stacking a second copy
        if (screen in backStack) {
            while (backStack.last() != screen) backStack.removeAt(backStack.lastIndex)
        } else {
            backStack.add(screen)
        }
        _currentScreen.value = screen
        when (screen) {
            RoomyScreen.RAM -> _ramStats.value = repository.getRamStats()
            RoomyScreen.JUNK -> refreshJunk()
            else -> Unit
        }
    }

    fun navigateBack(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        _currentScreen.value = backStack.last()
        return true
    }

    fun openPreview(items: List<MediaFileItem>, start: MediaFileItem) {
        _previewIds.value = items.map { it.id }
        _previewStartIndex.value = items.indexOfFirst { it.id == start.id }.coerceAtLeast(0)
        _previewSession.value += 1
        navigate(RoomyScreen.PREVIEW)
    }

    fun openCollection(collection: MediaCollection) {
        _activeCollection.value = collection
        navigate(RoomyScreen.COLLECTION)
    }

    /** Opens the compress screen for specific files, or for bulk compression when [items] is null. */
    fun openCompress(items: List<MediaFileItem>?) {
        if (!_compression.value.isRunning) {
            if (items != null || _compression.value.originalsReplaced) _compression.value = CompressionState()
            _compressTargetIds.value = items?.map { it.id }
        }
        navigate(RoomyScreen.BULK_COMPRESS)
    }

    // -------------------------------------------------------------
    // Permissions
    // -------------------------------------------------------------

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED

    private fun readMediaAccess(): MediaAccess = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            (granted(Manifest.permission.READ_MEDIA_IMAGES) || granted(Manifest.permission.READ_MEDIA_VIDEO)) -> MediaAccess.FULL
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> MediaAccess.PARTIAL
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
            granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> MediaAccess.FULL
        else -> MediaAccess.NONE
    }

    /** True once per ViewModel, so the permission prompt doesn't reappear on every rotation. */
    fun consumeAutoPrompt(): Boolean {
        if (autoPromptDone || _mediaAccess.value != MediaAccess.NONE) return false
        autoPromptDone = true
        return true
    }

    fun onPermissionResult(permanentlyDenied: Boolean) {
        autoPromptDone = true
        refreshAccess(forceRescan = true)
        _permissionPermanentlyDenied.value = permanentlyDenied && _mediaAccess.value == MediaAccess.NONE
    }

    fun onResume() {
        refreshAccess()
        refreshMetrics()
    }

    private fun refreshAccess(forceRescan: Boolean = false) {
        val now = readMediaAccess()
        val changed = now != _mediaAccess.value
        _mediaAccess.value = now
        if (now == MediaAccess.FULL) {
            _permissionPermanentlyDenied.value = false
            if (changed) repository.clearPickedItems()
        }
        if (changed || forceRescan) scanInventory()
    }

    fun addPickedMediaUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.addPickerUris(uris) }
            scanInventory()
        }
    }

    // -------------------------------------------------------------
    // Scanning
    // -------------------------------------------------------------

    fun refreshMetrics() {
        viewModelScope.launch {
            _storageMetrics.value = withContext(Dispatchers.IO) { repository.getStorageMetrics() }
            _ramStats.value = repository.getRamStats()
        }
    }

    /** Only one scan runs at a time; starting a new one cancels the old one. */
    fun scanInventory() {
        scanJob?.cancel()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            _isScanning.value = true
            _scanProgressText.value = "Looking for photos and videos…"
            try {
                val media = repository.scanMedia(keptIds.toSet(), compressedCopyIds.toSet())
                _allMedia.value = media
                _hasScanned.value = true

                _scanProgressText.value = "Checking for duplicates…"
                val groups = repository.findDuplicates(media) { _scanProgressText.value = it }
                _rawDuplicateGroups.value = groups
                val stillThere = groups.flatMapTo(HashSet()) { g -> g.files.map { it.id } }
                _duplicateSelection.update { selected -> selected.filterTo(HashSet()) { it in stillThere } }
                _duplicatesChecked.value = true
            } finally {
                if (scanJob === coroutineContext[Job]) {
                    _isScanning.value = false
                    _scanProgressText.value = ""
                }
            }
            refreshJunk()
            refreshMetrics()
        }
        scanJob = job
        job.start()
    }

    private fun scheduleRescan() {
        if (_mediaAccess.value == MediaAccess.NONE) return
        rescanDebounce?.cancel()
        rescanDebounce = viewModelScope.launch {
            delay(1500)
            if (_compression.value.isRunning || _systemRequest.value != null) rescanWhenIdle = true else scanInventory()
        }
    }

    private fun rescanIfPending() {
        if (rescanWhenIdle) {
            rescanWhenIdle = false
            scanInventory()
        }
    }

    fun refreshJunk() {
        viewModelScope.launch {
            _junkItems.value = repository.scanJunkItems(includeActiveTempFiles = !_compression.value.isRunning)
        }
    }

    // -------------------------------------------------------------
    // Review-First Actions: Keep
    // -------------------------------------------------------------

    fun keepItem(item: MediaFileItem) = setKept(item, true)

    fun unkeepItem(item: MediaFileItem) = setKept(item, false)

    private fun setKept(item: MediaFileItem, kept: Boolean) {
        if (kept) keptIds.add(item.id) else keptIds.remove(item.id)
        _allMedia.update { list ->
            list.map {
                if (it.id == item.id) it.copy(isExcluded = kept, reviewAction = if (kept) ReviewAction.KEEP else ReviewAction.NONE) else it
            }
        }
        viewModelScope.launch { repository.setKept(item.id, kept) }
    }

    // -------------------------------------------------------------
    // Deleting: Android's trash (recoverable for 30 days) on Android 11+
    // -------------------------------------------------------------

    fun requestDelete(items: List<MediaFileItem>, reason: DeleteReason = DeleteReason.USER) {
        if (items.isEmpty()) return
        if (_systemRequest.value != null || _deleteConfirmation.value != null) return

        val picked = items.filter { it.isPicked }
        val deletable = items.filter { !it.isPicked }
        if (picked.isNotEmpty()) {
            message("${plural(picked.size, "file")} added with the photo picker can't be deleted by Cleanser. Allow gallery access to manage them.")
        }
        if (deletable.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val kind = if (reason == DeleteReason.REPLACE_ORIGINALS) MediaRequestKind.DELETE else MediaRequestKind.TRASH
            openSystemRequest(deletable, kind, reason)
        } else {
            _deleteConfirmation.value = DeleteConfirmation(deletable, reason)
        }
    }

    private fun openSystemRequest(items: List<MediaFileItem>, kind: MediaRequestKind, reason: DeleteReason) {
        val sender = repository.createMediaRequest(items.map { it.uri }, kind)
        if (sender == null) {
            message("Android couldn't open its confirmation dialog for these files.")
            return
        }
        val requestKind = when (kind) {
            MediaRequestKind.TRASH -> SystemRequest.Kind.TRASH
            MediaRequestKind.DELETE -> SystemRequest.Kind.DELETE
            MediaRequestKind.RESTORE -> SystemRequest.Kind.RESTORE
        }
        _systemRequest.value = SystemRequest(nextRequestId++, sender, requestKind, items, reason)
    }

    /** The activity calls this before launching, so a rotation doesn't show the same dialog twice. */
    fun claimSystemRequestLaunch(request: SystemRequest): Boolean {
        if (request.id == launchedRequestId) return false
        launchedRequestId = request.id
        return true
    }

    fun onSystemRequestResult(ok: Boolean) {
        val request = _systemRequest.value ?: return
        _systemRequest.value = null
        viewModelScope.launch {
            when (request.kind) {
                SystemRequest.Kind.TRASH, SystemRequest.Kind.DELETE ->
                    if (ok) finishDeletion(request.items, trashed = request.kind == SystemRequest.Kind.TRASH, request.reason)
                SystemRequest.Kind.RESTORE -> if (ok) {
                    message("Restored ${plural(request.items.size, "file")}")
                    scanInventory()
                }
                SystemRequest.Kind.LEGACY_CONSENT -> {
                    val item = request.items.first()
                    if (ok) legacyConsented.add(item.id) else legacyQueue.removeFirstOrNull()
                    continueLegacyDelete()
                }
            }
            rescanIfPending()
        }
    }

    fun confirmDeletion() {
        val confirmation = _deleteConfirmation.value ?: return
        _deleteConfirmation.value = null
        legacyQueue.clear()
        legacyQueue.addAll(confirmation.items)
        legacyRequested = confirmation.items
        legacyReason = confirmation.reason
        legacyConsented.clear()
        viewModelScope.launch { continueLegacyDelete() }
    }

    fun dismissDeletion() {
        _deleteConfirmation.value = null
    }

    private suspend fun continueLegacyDelete() {
        while (legacyQueue.isNotEmpty()) {
            val item = legacyQueue.first()
            val result = repository.deleteDirect(item.uri)
            if (result is DirectDeleteResult.NeedsConsent && item.id !in legacyConsented) {
                _systemRequest.value = SystemRequest(nextRequestId++, result.intentSender, SystemRequest.Kind.LEGACY_CONSENT, listOf(item), legacyReason)
                return
            }
            legacyQueue.removeFirst()
        }
        finishDeletion(legacyRequested, trashed = false, legacyReason)
    }

    /** Checks what is really gone, updates the lists in place (no full rescan) and reports honestly. */
    private suspend fun finishDeletion(requested: List<MediaFileItem>, trashed: Boolean, reason: DeleteReason) {
        val gone = repository.filterGone(requested)
        val failed = requested.size - gone.size
        removeFromState(gone)
        val bytes = gone.sumOf { it.sizeBytes }

        if (gone.isEmpty()) {
            if (failed > 0) message("Couldn't delete ${plural(failed, "file")}.")
            return
        }
        if (!trashed) repository.forgetFiles(gone.map { it.id })
        val failedNote = if (failed > 0) " ${plural(failed, "file")} couldn't be deleted." else ""

        when {
            reason == DeleteReason.REPLACE_ORIGINALS -> {
                val goneIds = gone.mapTo(HashSet()) { it.id }
                val replaced = _compression.value.savedResults.filter { it.source.id in goneIds }
                val freed = replaced.sumOf { it.savedBytes }
                repository.renameCopiesToOriginalNames(replaced)
                repository.recordCleanup(freed, replaced.size, "compression")
                _compression.update { it.copy(originalsReplaced = true) }
                message("Replaced ${plural(replaced.size, "original")} with smaller copies. Freed ${StorageMetrics.formatBytes(freed)}.$failedNote")
                scanInventory()
            }
            trashed -> {
                repository.recordCleanup(bytes, gone.size, reason.name.lowercase())
                message(
                    "Moved ${plural(gone.size, "file")} (${StorageMetrics.formatBytes(bytes)}) to trash. Android empties it after 30 days.$failedNote",
                    "Undo"
                ) { openSystemRequest(gone, MediaRequestKind.RESTORE, reason) }
            }
            else -> {
                repository.recordCleanup(bytes, gone.size, reason.name.lowercase())
                message("Deleted ${plural(gone.size, "file")}, freed ${StorageMetrics.formatBytes(bytes)}.$failedNote")
            }
        }
        refreshMetrics()
    }

    private fun removeFromState(gone: List<MediaFileItem>) {
        if (gone.isEmpty()) return
        val ids = gone.mapTo(HashSet()) { it.id }
        keptIds.removeAll(ids)
        _allMedia.update { list -> list.filterNot { it.id in ids } }
        _duplicateSelection.update { it - ids }
        _rawDuplicateGroups.update { groups ->
            groups.mapNotNull { g ->
                val files = g.files.filterNot { it.id in ids }
                if (files.size < 2) return@mapNotNull null
                val keeper = files.firstOrNull { it.id == g.recommendedKeeperId } ?: recommendKeeper(files)
                g.copy(files = files, recommendedKeeperId = keeper.id)
            }
        }
    }

    // -------------------------------------------------------------
    // Exact Duplicates Selection & Deletion
    // -------------------------------------------------------------

    fun toggleDuplicateSelection(checksum: String, fileId: String) {
        _duplicateSelection.update { if (fileId in it) it - fileId else it + fileId }
    }

    /** Selects every copy except the recommended one in each group. */
    fun selectExtraDuplicateCopies() {
        _duplicateSelection.value = _rawDuplicateGroups.value
            .flatMap { g -> g.files.filter { it.id != g.recommendedKeeperId }.map { it.id } }
            .toSet()
    }

    fun clearDuplicateSelection() {
        _duplicateSelection.value = emptySet()
    }

    fun getSelectedDuplicates(): List<MediaFileItem> {
        return duplicateGroups.value.flatMap { group ->
            // Never delete every copy in a group
            val keepers = group.files.filter { !it.isSelectedForDelete }
            if (keepers.isNotEmpty()) group.files.filter { it.isSelectedForDelete } else emptyList()
        }
    }

    fun deleteSelectedDuplicates() = requestDelete(getSelectedDuplicates(), DeleteReason.DUPLICATES)

    // -------------------------------------------------------------
    // Large Files Threshold
    // -------------------------------------------------------------

    fun setLargeFileThresholdMb(mb: Int) {
        _largeFileThresholdMb.value = mb
    }

    // -------------------------------------------------------------
    // Compression Presets & Execution
    // -------------------------------------------------------------

    fun setCompressionPreset(preset: CompressionPreset) {
        _compressionPreset.value = preset
    }

    fun setBulkOptions(options: BulkCompressOptions) {
        _bulkOptions.value = options
    }

    private fun isCompressible(item: MediaFileItem) =
        !item.isExcluded && !item.isCompressedCopy && item.mimeType != "image/gif"

    fun startCompression(items: List<MediaFileItem>) {
        if (items.isEmpty() || _compression.value.isRunning) return
        val preset = _compressionPreset.value
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            _compression.value = CompressionState(isRunning = true, total = items.size)
            try {
                val (videos, images) = items.partition { it.isVideo }
                // Photos: two at a time (each is decoded at the target size, so memory stays bounded)
                coroutineScope {
                    val permits = Semaphore(2)
                    images.forEach { item ->
                        launch {
                            permits.withPermit {
                                _compression.update { it.copy(currentName = item.name) }
                                val result = repository.compress(item, preset) {}
                                _compression.update { it.copy(done = it.done + 1, results = it.results + result) }
                            }
                        }
                    }
                }
                // Videos: one at a time, the hardware encoder is the bottleneck
                for (video in videos) {
                    ensureActive()
                    _compression.update { it.copy(currentName = video.name, currentProgress = 0f) }
                    val result = repository.compress(video, preset) { p -> _compression.update { it.copy(currentProgress = p) } }
                    _compression.update { it.copy(done = it.done + 1, currentProgress = 0f, results = it.results + result) }
                }
            } finally {
                val cancelled = !isActive
                withContext(NonCancellable) {
                    val copies = _compression.value.results.mapNotNull { it.outputUri?.toString() }
                    compressedCopyIds.addAll(copies)
                    repository.markCompressedCopies(copies)
                }
                _compression.update { it.copy(isRunning = false, currentName = "", currentProgress = 0f, wasCancelled = cancelled) }
                compressionJob = null
                if (_compression.value.savedResults.isNotEmpty()) scanInventory() else rescanIfPending()
            }
        }
        compressionJob = job
        job.start()
    }

    fun cancelCompression() {
        compressionJob?.cancel()
    }

    /** Removes the originals of files that now have a smaller copy in the gallery (Android asks to confirm). */
    fun replaceOriginals() {
        val state = _compression.value
        if (state.isRunning || state.originalsReplaced) return
        requestDelete(state.savedResults.map { it.source }, DeleteReason.REPLACE_ORIGINALS)
    }

    fun dismissCompressionResults() {
        if (!_compression.value.isRunning) _compression.value = CompressionState()
    }

    // -------------------------------------------------------------
    // Junk Items Selection & Cleanup
    // -------------------------------------------------------------

    fun toggleJunkSelection(id: String) {
        val current = _junkItems.value
        _junkItems.value = current.map {
            if (it.id == id) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun deleteSelectedJunk() {
        val selected = _junkItems.value.filter { it.isSelected }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val freed = repository.deleteJunk(selected)
            repository.recordCleanup(freed, selected.size, "junk")
            message("Cleaned ${plural(selected.size, "file")}, freed ${StorageMetrics.formatBytes(freed)}.")
            refreshJunk()
            refreshMetrics()
        }
    }

    private fun plural(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"
}
