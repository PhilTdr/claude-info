package dev.treder.info.claude.data.jsonl

import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonlUsageParserTest {

    private val parser = JsonlUsageParser()

    @AfterTest
    fun cleanup() {
        tempPath?.deleteIfExists()
    }

    private var tempPath: java.nio.file.Path? = null

    @Test
    fun parsesAssistantLineWithCacheCreationSubObject() {
        val line = """{"type":"assistant","timestamp":"2026-05-04T13:26:28.910Z","message":{"model":"claude-opus-4-7","stop_reason":"tool_use","usage":{"input_tokens":6,"cache_creation_input_tokens":9463,"cache_read_input_tokens":30141,"output_tokens":143,"cache_creation":{"ephemeral_1h_input_tokens":9463,"ephemeral_5m_input_tokens":0}}}}"""
        val entry = parser.parseLine(line)
        assertNotNull(entry)
        assertEquals("claude-opus-4-7", entry.model)
        assertEquals(6L, entry.inputTokens)
        assertEquals(143L, entry.outputTokens)
        assertEquals(30141L, entry.cacheReadTokens)
        assertEquals(0L, entry.cacheCreation5mTokens)
        assertEquals(9463L, entry.cacheCreation1hTokens)
    }

    @Test
    fun extractsMessageIdAndRequestIdWhenPresent() {
        val line = """{"type":"assistant","timestamp":"2026-05-04T13:26:28.910Z","requestId":"req_abc","message":{"id":"msg_xyz","model":"claude-opus-4-7","stop_reason":"end_turn","usage":{"input_tokens":1,"output_tokens":1}}}"""
        val entry = parser.parseLine(line)
        assertNotNull(entry)
        assertEquals("msg_xyz", entry.messageId)
        assertEquals("req_abc", entry.requestId)
    }

    @Test
    fun acceptsAssistantLineWithoutStopReason() {
        // Subagent intermediate turns log assistant messages with stop_reason=null but valid
        // usage. These represent real billable usage and must be counted (ccusage does).
        // Dedup by (messageId, requestId) protects against double-counting when a later
        // completion line with the same key also appears.
        val line = """{"type":"assistant","timestamp":"2026-05-04T13:26:28.910Z","requestId":"req_a","message":{"id":"msg_a","model":"claude-opus-4-7","stop_reason":null,"usage":{"input_tokens":6,"output_tokens":1}}}"""
        val entry = parser.parseLine(line)
        assertNotNull(entry)
        assertEquals(6L, entry.inputTokens)
        assertEquals(1L, entry.outputTokens)
    }

    @Test
    fun ignoresNonAssistantLines() {
        val user = """{"type":"user","timestamp":"2026-05-04T13:26:28.910Z","message":{"role":"user","content":"hi"}}"""
        val system = """{"type":"system","subtype":"local_command","content":"<command-name>/skills</command-name>","level":"info","timestamp":"2026-05-08T13:11:16.562Z"}"""
        assertNull(parser.parseLine(user))
        assertNull(parser.parseLine(system))
    }

    @Test
    fun fallsBackToCacheCreationInputTokensWhenSubObjectMissing() {
        val line = """{"type":"assistant","timestamp":"2026-05-04T13:26:28.910Z","message":{"model":"claude-sonnet-4-6","stop_reason":"end_turn","usage":{"input_tokens":10,"output_tokens":20,"cache_creation_input_tokens":500,"cache_read_input_tokens":0}}}"""
        val entry = parser.parseLine(line)
        assertNotNull(entry)
        assertEquals(500L, entry.cacheCreation5mTokens)
        assertEquals(0L, entry.cacheCreation1hTokens)
    }

    @Test
    fun tolerantToMalformedLines() {
        assertNull(parser.parseLine("not valid json"))
        assertNull(parser.parseLine(""))
        assertNull(parser.parseLine("{}"))
    }

    @Test
    fun parseFromOffsetReturnsCompletedLinesOnly() {
        val file = Files.createTempFile("claude-info-parser-", ".jsonl")
        tempPath = file
        val full = """{"type":"assistant","timestamp":"2026-05-04T13:26:28.910Z","message":{"model":"claude-opus-4-7","stop_reason":"end_turn","usage":{"input_tokens":1,"output_tokens":1,"cache_read_input_tokens":0,"cache_creation_input_tokens":0}}}"""
        Files.writeString(file, full + "\n")

        val result = parser.parseFromOffset(file, 0L)
        assertEquals(1, result.entries.size)
        assertTrue(result.newOffset > 0L)

        val afterOffset = parser.parseFromOffset(file, result.newOffset)
        assertEquals(0, afterOffset.entries.size)
        assertEquals(result.newOffset, afterOffset.newOffset)
    }

    @Test
    fun parseFromOffsetIgnoresIncompleteTrailingLine() {
        val file = Files.createTempFile("claude-info-parser-", ".jsonl")
        tempPath = file
        val complete = """{"type":"assistant","timestamp":"2026-05-04T13:26:28.910Z","message":{"model":"claude-opus-4-7","stop_reason":"end_turn","usage":{"input_tokens":1,"output_tokens":1}}}"""
        val incompletePrefix = """{"type":"assistant","timestamp":"2026-05-04T13:26:29.000Z","message":{"model":"claude-opus-4-7","""
        Files.writeString(file, complete + "\n" + incompletePrefix)

        val result = parser.parseFromOffset(file, 0L)
        assertEquals(1, result.entries.size)

        // append the rest of the previously-incomplete line
        val tail = """"stop_reason":"end_turn","usage":{"input_tokens":2,"output_tokens":2}}}""" + "\n"
        Files.write(file, tail.toByteArray(), StandardOpenOption.APPEND)

        val next = parser.parseFromOffset(file, result.newOffset)
        assertEquals(1, next.entries.size)
        assertEquals(2L, next.entries.first().inputTokens)
    }
}
