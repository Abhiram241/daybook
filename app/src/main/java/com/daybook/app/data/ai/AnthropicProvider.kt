package com.daybook.app.data.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** DAILY_REPORT_PLAN.md §3.3 — the Messages API shape (`/v1/messages`), `x-api-key` header instead
 *  of `Authorization: Bearer`, plus the `anthropic-version` header every call requires.
 *
 *  Round 2 (Feature 4) — real multi-turn: Anthropic's Messages API takes `system` as its own
 *  top-level string field, never inside the `messages` array, so [chat] lifts any SYSTEM-role
 *  turns out into that field and sends the rest (user/assistant, alternating) as `messages`.
 *
 *  Round 2 (Feature 1) — Anthropic has no free tier; [listModels] returns every model the key can
 *  see, unfiltered, no "free" labelling (per the plan). */
class AnthropicProvider(
    private val client: OkHttpClient = OpenAiCompatibleProvider.defaultClient
) : AiProvider {

    override val id: String = "Anthropic"

    @Serializable
    private data class Message(val role: String, val content: String)

    @Serializable
    private data class MessagesRequest(
        val model: String,
        val max_tokens: Int,
        val messages: List<Message>,
        val system: String? = null
    )

    @Serializable
    private data class ContentBlock(val type: String? = null, val text: String? = null)

    @Serializable
    private data class MessagesResponse(val content: List<ContentBlock> = emptyList(), val error: ErrorBody? = null)

    @Serializable
    private data class ErrorBody(val message: String? = null)

    override suspend fun complete(apiKey: String, model: String, prompt: String): AiResult =
        chat(apiKey, model, listOf(AiChatMessage(AiChatRole.USER, prompt)))

    override suspend fun chat(apiKey: String, model: String, messages: List<AiChatMessage>): AiResult =
        withContext(Dispatchers.IO) {
            try {
                val system = messages.filter { it.role == AiChatRole.SYSTEM }
                    .joinToString("\n\n") { it.content }
                    .takeIf { it.isNotBlank() }
                val turns = messages.filter { it.role != AiChatRole.SYSTEM }
                    .map { Message(if (it.role == AiChatRole.ASSISTANT) "assistant" else "user", it.content) }
                val body = json.encodeToString(
                    MessagesRequest.serializer(),
                    MessagesRequest(model = model, max_tokens = 1024, messages = turns, system = system)
                )
                val request = Request.Builder()
                    .url("https://api.anthropic.com/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("Content-Type", "application/json")
                    .post(body.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext AiResult.Failure(friendlyHttpError(id, response.code, text))
                    }
                    val parsed = runCatching { json.decodeFromString(MessagesResponse.serializer(), text) }.getOrNull()
                    val content = parsed?.content?.joinToString("") { it.text.orEmpty() }?.trim()
                    if (content.isNullOrBlank()) {
                        Log.e(TAG, "empty/unparseable response body: $text")
                        AiResult.Failure("$id returned an empty response. Try again in a moment.")
                    } else {
                        AiResult.Success(content)
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "network failure", e)
                AiResult.Failure("Couldn't reach $id. Check your connection and try again.")
            } catch (e: Exception) {
                Log.e(TAG, "unexpected failure", e)
                AiResult.Failure("Something went wrong talking to $id.")
            }
        }

    override suspend fun listModels(apiKey: String): AiModelListResult =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.anthropic.com/v1/models")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext AiModelListResult.Failure(friendlyHttpError(id, response.code, text))
                    }
                    val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                        ?: return@withContext AiModelListResult.Failure(
                            "$id returned an unexpected model list format."
                        )
                    val dataArray = (root["data"] as? kotlinx.serialization.json.JsonArray).orEmpty()
                    val models = dataArray.mapNotNull { el ->
                        val obj = el as? JsonObject ?: return@mapNotNull null
                        val modelId = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        // No free tier exists for Anthropic (per the plan) — every model is shown
                        // unfiltered, no free/paid label.
                        AiModel(id = modelId, free = null)
                    }.sortedBy { it.id }
                    if (models.isEmpty()) AiModelListResult.Failure("$id didn't return any models for this key.")
                    else AiModelListResult.Success(models)
                }
            } catch (e: IOException) {
                Log.e(TAG, "network failure listing models", e)
                AiModelListResult.Failure("Couldn't reach $id. Check your connection and try again.")
            } catch (e: Exception) {
                Log.e(TAG, "unexpected failure listing models", e)
                AiModelListResult.Failure("Something went wrong listing $id's models.")
            }
        }

    private companion object {
        const val TAG = "AnthropicProvider"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val json = Json { ignoreUnknownKeys = true }
    }
}
