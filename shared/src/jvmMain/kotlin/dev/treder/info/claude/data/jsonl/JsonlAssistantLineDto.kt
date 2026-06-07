package dev.treder.info.claude.data.jsonl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class JsonlLineDto(
    val type: String? = null,
    val timestamp: String? = null,
    @SerialName("requestId") val requestId: String? = null,
    val message: AssistantMessageDto? = null,
)

@Serializable
internal data class AssistantMessageDto(
    val id: String? = null,
    val model: String? = null,
    @SerialName("stop_reason") val stopReason: String? = null,
    val usage: UsageDto? = null,
)

@Serializable
internal data class UsageDto(
    @SerialName("input_tokens") val inputTokens: Long? = null,
    @SerialName("output_tokens") val outputTokens: Long? = null,
    @SerialName("cache_read_input_tokens") val cacheReadInputTokens: Long? = null,
    @SerialName("cache_creation_input_tokens") val cacheCreationInputTokens: Long? = null,
    @SerialName("cache_creation") val cacheCreation: CacheCreationDto? = null,
)

@Serializable
internal data class CacheCreationDto(
    @SerialName("ephemeral_5m_input_tokens") val ephemeral5mInputTokens: Long? = null,
    @SerialName("ephemeral_1h_input_tokens") val ephemeral1hInputTokens: Long? = null,
)
