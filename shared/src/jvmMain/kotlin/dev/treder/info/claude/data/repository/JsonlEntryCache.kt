package dev.treder.info.claude.data.repository

import dev.treder.info.claude.data.jsonl.JsonlPaths
import dev.treder.info.claude.data.jsonl.JsonlUsageParser
import dev.treder.info.claude.domain.model.UsageEntry
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class JsonlEntryCache(
    private val parser: JsonlUsageParser = JsonlUsageParser(),
    private val rootDir: Path = JsonlPaths.claudeProjectsDir,
) {

    private data class FileState(
        var offset: Long,
        var lastModified: FileTime,
        val entries: MutableList<UsageEntry>,
    )

    private val files = LinkedHashMap<Path, FileState>()
    private val mutex = Mutex()

    suspend fun refresh(): List<UsageEntry> = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!Files.exists(rootDir)) return@withContext emptyList<UsageEntry>()

            val current = scanJsonlFiles(rootDir)

            val iterator = files.keys.iterator()
            while (iterator.hasNext()) {
                if (iterator.next() !in current) iterator.remove()
            }

            for (path in current) {
                val attrs = runCatching { Files.readAttributes(path, BasicFileAttributes::class.java) }.getOrNull()
                    ?: continue
                val state = files[path]
                if (state == null) {
                    val result = parser.parseFromOffset(path, 0L)
                    files[path] = FileState(
                        offset = result.newOffset,
                        lastModified = attrs.lastModifiedTime(),
                        entries = result.entries.toMutableList(),
                    )
                } else {
                    val changed = attrs.lastModifiedTime() != state.lastModified || attrs.size() > state.offset
                    if (changed) {
                        val result = parser.parseFromOffset(path, state.offset)
                        state.offset = result.newOffset
                        state.entries.addAll(result.entries)
                        state.lastModified = attrs.lastModifiedTime()
                    }
                }
            }

            deduplicate(files.values.flatMap { it.entries })
        }
    }

    private fun deduplicate(entries: List<UsageEntry>): List<UsageEntry> {
        if (entries.isEmpty()) return entries
        val byKey = LinkedHashMap<String, UsageEntry>(entries.size)
        val untracked = ArrayList<UsageEntry>()
        for (entry in entries) {
            val messageId = entry.messageId ?: run {
                untracked.add(entry)
                continue
            }
            val key = "$messageId:${entry.requestId.orEmpty()}"
            val existing = byKey[key]
            if (existing == null || entry.totalTokens() > existing.totalTokens()) {
                byKey[key] = entry
            }
        }
        return ArrayList<UsageEntry>(byKey.size + untracked.size).apply {
            addAll(byKey.values)
            addAll(untracked)
        }
    }

    private fun UsageEntry.totalTokens(): Long =
        inputTokens + outputTokens + cacheReadTokens + cacheCreation5mTokens + cacheCreation1hTokens

    private fun scanJsonlFiles(root: Path): Set<Path> {
        val result = LinkedHashSet<Path>()
        Files.walk(root).use { stream ->
            stream
                .filter { path ->
                    val name = path.fileName?.toString() ?: return@filter false
                    name.endsWith(".jsonl") && Files.isRegularFile(path)
                }
                .forEach { result.add(it) }
        }
        return result
    }
}
