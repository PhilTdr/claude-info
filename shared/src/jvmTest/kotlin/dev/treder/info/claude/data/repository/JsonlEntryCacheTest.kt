package dev.treder.info.claude.data.repository

import dev.treder.info.claude.data.jsonl.JsonlUsageParser
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class JsonlEntryCacheTest {

    private lateinit var projectsRoot: Path
    private lateinit var jsonlFile: Path

    private val sampleLine = """{"type":"assistant","timestamp":"2026-05-04T13:26:28.910Z","message":{"model":"claude-opus-4-7","stop_reason":"end_turn","usage":{"input_tokens":1,"output_tokens":2,"cache_read_input_tokens":0,"cache_creation_input_tokens":0}}}"""

    @BeforeTest
    fun setUp() {
        projectsRoot = Files.createTempDirectory("claude-info-cache-")
        val subdir = Files.createDirectory(projectsRoot.resolve("session"))
        jsonlFile = subdir.resolve("session-1.jsonl")
        Files.writeString(jsonlFile, sampleLine + "\n")
    }

    @AfterTest
    fun tearDown() {
        Files.walk(projectsRoot).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }

    @Test
    fun firstRefreshReadsInitialEntries() = runBlocking {
        val cache = JsonlEntryCache(parser = JsonlUsageParser(), rootDir = projectsRoot)
        val entries = cache.refresh()
        assertEquals(1, entries.size)
    }

    @Test
    fun secondRefreshAfterAppendReturnsAccumulatedEntries() = runBlocking {
        val cache = JsonlEntryCache(parser = JsonlUsageParser(), rootDir = projectsRoot)
        assertEquals(1, cache.refresh().size)

        Files.write(jsonlFile, (sampleLine + "\n").toByteArray(), StandardOpenOption.APPEND)
        val secondRefresh = cache.refresh()
        assertEquals(2, secondRefresh.size)
    }

    @Test
    fun dedupKeepsHighestTokenEntryForSamePair() = runBlocking {
        // Same (messageId, requestId) appears twice within a session — first as a streaming
        // partial with a small output_tokens count, then as the final completion. ccusage
        // resolves the tie by picking the highest-token entry; we mirror that.
        val partial = """{"type":"assistant","timestamp":"2026-05-21T14:11:02.074Z","requestId":"req_1","message":{"id":"msg_1","model":"claude-opus-4-7","stop_reason":"end_turn","usage":{"input_tokens":7,"output_tokens":3,"cache_creation_input_tokens":490,"cache_read_input_tokens":34196}}}"""
        val final = """{"type":"assistant","timestamp":"2026-05-21T14:11:02.215Z","requestId":"req_1","message":{"id":"msg_1","model":"claude-opus-4-7","stop_reason":"end_turn","usage":{"input_tokens":7,"output_tokens":120,"cache_creation_input_tokens":490,"cache_read_input_tokens":34196}}}"""
        Files.writeString(jsonlFile, partial + "\n" + final + "\n")

        val cache = JsonlEntryCache(parser = JsonlUsageParser(), rootDir = projectsRoot)
        val entries = cache.refresh()
        assertEquals(1, entries.size)
        assertEquals(120L, entries.first().outputTokens)
    }

    @Test
    fun deduplicatesEntriesAcrossFilesByMessageAndRequestId() = runBlocking {
        val sharedLine = """{"type":"assistant","timestamp":"2026-05-04T13:26:28.910Z","requestId":"req_1","message":{"id":"msg_1","model":"claude-opus-4-7","stop_reason":"end_turn","usage":{"input_tokens":10,"output_tokens":20}}}"""
        Files.writeString(jsonlFile, sharedLine + "\n")
        val sibling = projectsRoot.resolve("session").resolve("session-resumed.jsonl")
        Files.writeString(sibling, sharedLine + "\n")

        val cache = JsonlEntryCache(parser = JsonlUsageParser(), rootDir = projectsRoot)
        val entries = cache.refresh()
        assertEquals(1, entries.size)
    }

    @Test
    fun newFileIsPickedUpOnRefresh() = runBlocking {
        val cache = JsonlEntryCache(parser = JsonlUsageParser(), rootDir = projectsRoot)
        cache.refresh()

        val anotherFile = projectsRoot.resolve("session").resolve("session-2.jsonl")
        Files.writeString(anotherFile, sampleLine + "\n")

        val entries = cache.refresh()
        assertEquals(2, entries.size)
    }
}
