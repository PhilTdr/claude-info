package dev.treder.info.claude.data.jsonl

import dev.treder.info.claude.domain.model.UsageEntry
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Instant
import kotlinx.serialization.json.Json

class JsonlUsageParser(
    private val json: Json = defaultJson,
) {

    data class ParseResult(val newOffset: Long, val entries: List<UsageEntry>)

    fun parseFromOffset(path: Path, startOffset: Long): ParseResult {
        if (!Files.exists(path)) return ParseResult(startOffset, emptyList())

        val length = Files.size(path)
        if (length <= startOffset) return ParseResult(length, emptyList())

        val available = (length - startOffset).toInt()
        val buffer = ByteArray(available)
        RandomAccessFile(path.toFile(), "r").use { raf ->
            raf.seek(startOffset)
            raf.readFully(buffer)
        }

        var lastNewlineIndex = -1
        for (i in buffer.indices.reversed()) {
            if (buffer[i] == NEWLINE_BYTE) {
                lastNewlineIndex = i
                break
            }
        }
        if (lastNewlineIndex < 0) {
            // No complete line yet — leave offset unchanged so we re-read on next refresh.
            return ParseResult(startOffset, emptyList())
        }

        val consumedBytes = lastNewlineIndex + 1
        val processable = buffer.copyOf(consumedBytes)
        val text = String(processable, Charsets.UTF_8)

        val entries = ArrayList<UsageEntry>()
        for (line in text.split('\n')) {
            if (line.isBlank()) continue
            parseLine(line)?.let(entries::add)
        }

        return ParseResult(startOffset + consumedBytes.toLong(), entries)
    }

    internal fun parseLine(line: String): UsageEntry? {
        val dto = runCatching { json.decodeFromString(JsonlLineDto.serializer(), line) }.getOrNull() ?: return null
        if (dto.type != "assistant") return null
        val msg = dto.message ?: return null
        // Subagent transcripts emit assistant lines with valid usage but stop_reason=null.
        // ccusage counts them; we used to drop them. Dedup by (messageId, requestId) keeps
        // us safe against double-counting when a sibling line with stop_reason!=null exists.
        val usage = msg.usage ?: return null
        val model = msg.model ?: return null
        val timestamp = dto.timestamp ?: return null
        val instant = runCatching { Instant.parse(timestamp) }.getOrNull() ?: return null

        val c5m: Long
        val c1h: Long
        if (usage.cacheCreation != null) {
            c5m = usage.cacheCreation.ephemeral5mInputTokens ?: 0L
            c1h = usage.cacheCreation.ephemeral1hInputTokens ?: 0L
        } else {
            c5m = usage.cacheCreationInputTokens ?: 0L
            c1h = 0L
        }

        return UsageEntry(
            timestamp = instant,
            model = model,
            inputTokens = usage.inputTokens ?: 0L,
            outputTokens = usage.outputTokens ?: 0L,
            cacheReadTokens = usage.cacheReadInputTokens ?: 0L,
            cacheCreation5mTokens = c5m,
            cacheCreation1hTokens = c1h,
            messageId = msg.id,
            requestId = dto.requestId,
        )
    }

    companion object {
        private const val NEWLINE_BYTE: Byte = 0x0A

        val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }
}
