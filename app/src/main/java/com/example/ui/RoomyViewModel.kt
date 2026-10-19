package com.example.ui

import android.app.Application
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

enum class RoomyScreen {
    HOME,
    REVIEW,
    PHOTOS,
    VIDEOS,
    LARGE_FILES,
    DUPLICATES,
    BULK_COMPRESS,
    JUNK,
    RAM
}

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
            val media = repository.scanAccessibleMedia { _, text ->
                _scanProgressText.value = text
            }
            _allMedia.value = media

            _scanProgressText.value = "Detecting exact duplicates (SHA-256)…"
            val duplicates = repository.detectExactDuplicates(media) { _, text ->
                _scanProgressText.value = text
            }
            _duplicateGroups.value = duplicates

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

    fun deleteItem(item: MediaFileItem) {
        viewModelScope.launch {
            repository.deleteFiles(listOf(item))
            _allMedia.value = _allMedia.value.filter { it.id != item.id }
            refreshMetrics()
            // Also refresh duplicates
            _duplicateGroups.value = repository.detectExactDuplicates(_allMedia.value) { _, _ -> }
        }
    }

    fun deleteBatch(items: List<MediaFileItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            repository.deleteFiles(items)
            val deletedIds = items.map { it.id }.toSet()
            _allMedia.value = _allMedia.value.filter { !deletedIds.contains(it.id) }
            refreshMetrics()
            _duplicateGroups.value = repository.detectExactDuplicates(_allMedia.value) { _, _ -> }
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

    fun deleteSelectedDuplicates() {
        val selectedFiles = _duplicateGroups.value.flatMap { group ->
            // Prevent deleting ALL files in a group per PRD
            val keepers = group.files.filter { !it.isSelectedForDelete }
            if (keepers.isNotEmpty()) {
                group.files.filter { it.isSelectedForDelete }
            } else emptyList()
        }

        if (selectedFiles.isNotEmpty()) {
            deleteBatch(selectedFiles)
        }
    }

    // -------------------------------------------------------------
    // Large Files Threshold & Selection
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
