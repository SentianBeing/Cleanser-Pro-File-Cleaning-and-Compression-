package com.example.data

import android.content.Context
import android.net.Uri
import com.example.model.DuplicateGroup
import com.example.model.MediaFileItem
import com.example.model.recommendKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Finds byte-identical files in three cheap-to-expensive steps:
 * 1. same size (free, from MediaStore),
 * 2. same hash of the first and last 64 KB,
 * 3. same SHA-256 of the whole file.
 * Hashes are cached in Room, so later scans only read new or changed files.
 */
class DuplicateFinder(private val context: Context, private val dao: MediaDao) {

    suspend fun find(
        media: List<MediaFileItem>,
        onProgress: (String) -> Unit
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val candidates = media
            .filter { it.sizeBytes > 0 && !it.isPicked }
            .groupBy { it.sizeBytes }
            .values
            .filter { it.size > 1 }
            .flatten()

        val cached = dao.getHashes().associateBy { it.id }
        pruneStaleHashes(cached.keys, media)
        if (candidates.isEmpty()) return@withContext emptyList()

        val updated = ConcurrentHashMap<String, FileHashEntity>()
        fun validCache(item: MediaFileItem) = cached[item.id]
            ?.takeIf { it.sizeBytes == item.sizeBytes && it.dateModified == item.dateAdded }

        // Step 2: quick fingerprint of the start and end of each candidate
        val checked = AtomicInteger(0)
        val partials = candidates.mapParallel { item ->
            val hash = validCache(item)?.partialHash ?: partialHash(item.uri, item.sizeBytes)?.also { partial ->
                val isWholeFile = item.sizeBytes <= 2L * EDGE_BYTES
                updated[item.id] = FileHashEntity(item.id, item.sizeBytes, item.dateAdded, partial, if (isWholeFile) partial else null)
            }
            val n = checked.incrementAndGet()
            if (n % 25 == 0) onProgress("Checking for duplicates… $n of ${candidates.size}")
            item to hash
        }

        // Step 3: full hash only where size and fingerprint both match
        val fullCandidates = partials
            .filter { it.second != null }
            .groupBy { (item, partial) -> "${item.sizeBytes}:$partial" }
            .values
            .filter { it.size > 1 }
            .flatten()
            .map { it.first }

        if (fullCandidates.isNotEmpty()) onProgress("Confirming ${fullCandidates.size} possible duplicates…")
        val fulls = fullCandidates.mapParallel { item ->
            val entry = updated[item.id] ?: validCache(item)
            val full = entry?.fullHash ?: fullHash(item.uri)?.also { full ->
                entry?.let { updated[item.id] = it.copy(fullHash = full) }
            }
            item to full
        }

        if (updated.isNotEmpty()) dao.upsertHashes(updated.values.toList())

        fulls.filter { it.second != null }
            .groupBy { it.second!! }
            .filter { it.value.size > 1 }
            .map { (hash, matches) ->
                val files = matches.map { it.first.copy(sha256 = hash) }
                val keeper = recommendKeeper(files)
                DuplicateGroup(
                    checksum = hash,
                    byteSize = files.first().sizeBytes,
                    isVideo = files.first().isVideo,
                    files = listOf(keeper) + files.filter { it.id != keeper.id }.sortedBy { it.displayDate },
                    recommendedKeeperId = keeper.id
                )
            }
            .sortedByDescending { it.byteSize * (it.files.size - 1) }
    }

    private suspend fun pruneStaleHashes(cachedIds: Set<String>, media: List<MediaFileItem>) {
        val current = media.mapTo(HashSet()) { it.id }
        val stale = cachedIds.filter { it !in current }
        stale.chunked(500).forEach { dao.deleteHashes(it) }
    }

    /** SHA-256 of the size plus the first and last 64 KB. */
    private fun partialHash(uri: Uri, size: Long): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(size.toString().toByteArray())
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                val buffer = ByteBuffer.allocate(EDGE_BYTES)
                fun readAt(position: Long) {
                    buffer.clear()
                    channel.position(position)
                    while (buffer.hasRemaining() && channel.read(buffer) > 0) Unit
                    buffer.flip()
                    digest.update(buffer)
                }
                readAt(0)
                if (size > EDGE_BYTES) readAt((size - EDGE_BYTES).coerceAtLeast(EDGE_BYTES.toLong()))
            }
            digest.digest().toHex()
        }
    } catch (_: Exception) {
        null
    }

    private fun fullHash(uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { it.sha256() }
    } catch (_: Exception) {
        null
    }

    private fun InputStream.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(256 * 1024)
        while (true) {
            val read = read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().toHex()
    }

    private suspend fun <T, R> List<T>.mapParallel(transform: suspend (T) -> R): List<R> = coroutineScope {
        val permits = Semaphore(PARALLEL_READS)
        map { item ->
            async {
                permits.withPermit {
                    ensureActive()
                    transform(item)
                }
            }
        }.awaitAll()
    }

    private companion object {
        const val EDGE_BYTES = 64 * 1024
        const val PARALLEL_READS = 4
        val HEX = "0123456789abcdef".toCharArray()

        fun ByteArray.toHex(): String {
            val out = CharArray(size * 2)
            forEachIndexed { i, b ->
                val v = b.toInt() and 0xFF
                out[i * 2] = HEX[v ushr 4]
                out[i * 2 + 1] = HEX[v and 0x0F]
            }
            return String(out)
        }
    }
}
