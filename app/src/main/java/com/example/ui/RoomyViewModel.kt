package com.example.ui

import android.app.Application
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.RoomyRepository
import com.example.model.CompressionJobResult
import com.example.model.CompressionPreset
import com.example.model.DuplicateGroup
import com.example.model.JunkItem
import com.example.model.MediaFileItem
import com.example.model.RamStats
import com.example.model.ReviewAction
import com.example.model.StorageMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RoomyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RoomyRepository(application)

    private val _currentScreen = MutableStateFlow(RoomyScreen.HOME)
    val currentScreen: StateFlow<RoomyScreen> = _currentScreen.asStateFlow()

    private val _storageMetrics = MutableStateFlow(StorageMetrics())
    val storageMetrics: StateFlow<StorageMetrics> = _storageMetrics.asStateFlow()

    private val _ramStats = MutableStateFlow(RamStats())
    val ramStats: StateFlow<RamStats> = _ramStats.asStateFlow()

    private val _allMedia = MutableStateFlow<List<MediaFileItem>>(emptyList())
    val allMedia: StateFlow<List<MediaFileItem>> = _allMedia.asStateFlow()

    private val _duplicateGroups = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val duplicateGroups: StateFlow<List<DuplicateGroup>> = _duplicateGroups.asStateFlow()

    private val _junkItems = MutableStateFlow<List<JunkItem>>(emptyList())
    val junkItems: StateFlow<List<JunkItem>> = _junkItems.asStateFlow()

    private val _largeFileThresholdMb = MutableStateFlow(100) // Default 100 MB per PRD
    val largeFileThresholdMb: StateFlow<Int> = _largeFileThresholdMb.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgressText = MutableStateFlow("")
    val scanProgressText: StateFlow<String> = _scanProgressText.asStateFlow()

    private val _hasMediaPermission = MutableStateFlow(checkInitialPermission())
    val hasMediaPermission: StateFlow<Boolean> = _hasMediaPermission.asStateFlow()

    // Compression State
    private val _compressionPreset = MutableStateFlow(CompressionPreset.BALANCED)
    val compressionPreset: StateFlow<CompressionPreset> = _compressionPreset.asStateFlow()

    private val _isCompressing = MutableStateFlow(false)
    val isCompressing: StateFlow<Boolean> = _isCompressing.asStateFlow()

    private val _compressionProgress = MutableStateFlow(0f)
    val compressionProgress: StateFlow<Float> = _compressionProgress.asStateFlow()

    private val _compressionResults = MutableStateFlow<List<CompressionJobResult>>(emptyList())
    val compressionResults: StateFlow<List<CompressionJobResult>> = _compressionResults.asStateFlow()

    init {
        refreshMetrics()
        scanInventory()
    }

    private fun checkInitialPermission(): Boolean {
        val app = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val img = ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_MEDIA_IMAGES)
            val vid = ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_MEDIA_VIDEO)
            img == PackageManager.PERMISSION_GRANTED || vid == PackageManager.PERMISSION_GRANTED
        } else {
            val ext = ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_EXTERNAL_STORAGE)
            ext == PackageManager.PERMISSION_GRANTED
        }
    }

    fun updatePermissionState(granted: Boolean) {
        _hasMediaPermission.value = granted
        if (granted) {
            scanInventory()
        }
    }

    fun checkPermissionsAndRefresh() {
        val granted = checkInitialPermission()
        if (granted != _hasMediaPermission.value) {
            _hasMediaPermission.value = granted
            if (granted) {
                scanInventory()
            }
        }
    }

    fun addPickedMediaUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        repository.addPickerUris(uris)
        scanInventory()
    }

    fun navigate(screen: RoomyScreen) {
        _currentScreen.value = screen
        if (screen == RoomyScreen.RAM) {
            _ramStats.value = repository.getRamStats()
        }
        if (screen == RoomyScreen.JUNK) {
            refreshJunk()
        }
    }

    fun navigateBack() {
        _currentScreen.value = RoomyScreen.HOME
    }

    fun refreshMetrics() {
        viewModelScope.launch {
            _storageMetrics.value = repository.getStorageMetrics()
            _ramStats.value = repository.getRamStats()
        }
    }

    fun scanInventory() {
        viewModelScope.launch {
            _isScanning.value = true
            val media = repository.scanRealMedia { _, text ->
                _scanProgressText.value = text
            }
            _allMedia.value = media

            if (media.isNotEmpty()) {
                _scanProgressText.value = "Detecting exact duplicates (SHA-256)…"
                val duplicates = repository.detectExactDuplicates(media) { _, text ->
                    _scanProgressText.value = text
                }
                _duplicateGroups.value = duplicates
            } else {
                _duplicateGroups.value = emptyList()
            }

            refreshJunk()
            refreshMetrics()
            _isScanning.value = false
        }
    }

    fun refreshJunk() {
        viewModelScope.launch {
            _junkItems.value = repository.scanJunkItems()
        }
    }

    // -------------------------------------------------------------
    // Review-First Actions: Keep / Delete / Compress
    // -------------------------------------------------------------

    fun keepItem(item: MediaFileItem) {
        repository.keepItem(item.id)
        val updated = _allMedia.value.map {
            if (it.id == item.id) it.copy(isExcluded = true, reviewAction = ReviewAction.KEEP) else it
        }
        _allMedia.value = updated
    }

    fun unkeepItem(item: MediaFileItem) {
        repository.unkeepItem(item.id)
        val updated = _allMedia.value.map {
            if (it.id == item.id) it.copy(isExcluded = false, reviewAction = ReviewAction.NONE) else it
        }
        _allMedia.value = updated
    }

    /**
     * Builds official Android MediaStore system deletion request for modern Android
     */
    fun createDeleteIntentSender(items: List<MediaFileItem>): IntentSender? {
        val uris = items.map { it.uri }
        return repository.createSystemDeleteIntent(uris)
    }

    /**
     * Executed when direct deletion is appropriate or after OS permission approval
     */
    fun onDeletionCompleted(items: List<MediaFileItem>) {
        viewModelScope.launch {
            val uris = items.map { it.uri }
            repository.deleteDirect(uris)
            scanInventory()
        }
    }

    // -------------------------------------------------------------
    // Exact Duplicates Selection & Deletion
    // -------------------------------------------------------------

    fun toggleDuplicateSelection(checksum: String, fileId: String) {
        val current = _duplicateGroups.value
        val updated = current.map { group ->
            if (group.checksum == checksum) {
                val updatedFiles = group.files.map { f ->
                    if (f.id == fileId) f.copy(isSelectedForDelete = !f.isSelectedForDelete) else f
                }
                group.copy(files = updatedFiles)
            } else group
        }
        _duplicateGroups.value = updated
    }

    fun getSelectedDuplicates(): List<MediaFileItem> {
        return _duplicateGroups.value.flatMap { group ->
            val keepers = group.files.filter { !it.isSelectedForDelete }
            if (keepers.isNotEmpty()) {
                group.files.filter { it.isSelectedForDelete }
            } else emptyList()
        }
    }

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

    fun compressSingleItem(item: MediaFileItem) {
        viewModelScope.launch {
            _isCompressing.value = true
            _compressionProgress.value = 0.2f
            val res = repository.compressMediaItem(item, _compressionPreset.value) { p, _ ->
                _compressionProgress.value = p
            }
            _compressionResults.value = listOf(res)
            _isCompressing.value = false
            refreshMetrics()
        }
    }

    fun compressBatch(items: List<MediaFileItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            _isCompressing.value = true
            val results = mutableListOf<CompressionJobResult>()
            val count = items.size.coerceAtLeast(1)

            items.forEachIndexed { index, item ->
                _compressionProgress.value = index.toFloat() / count
                val res = repository.compressMediaItem(item, _compressionPreset.value) { _, _ -> }
                results.add(res)
            }

            _compressionResults.value = results
            _isCompressing.value = false
            refreshMetrics()
        }
    }

    fun dismissCompressionResults() {
        _compressionResults.value = emptyList()
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
            repository.deleteJunk(selected)
            refreshJunk()
            refreshMetrics()
        }
    }
}
