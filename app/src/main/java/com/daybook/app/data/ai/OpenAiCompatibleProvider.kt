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
import java.util.concurrent.TimeUnit

/**
 * DAILY_REPORT_PLAN.md §3.3 — one adapter for every provider that speaks the OpenAI
 * `POST /chat/completions` shape: OpenAI, OpenRouter, NVIDIA NIM, OpenCode, OpenCode Zen.
 *
 * D0 confirmation (web docs, Sept 2026):
 *  - **OpenCode Zen** is documented and CONFIRMED OpenAI-compatible at
 *    `https://opencode.ai/zen/v1/chat/completions` (opencode.ai/docs/zen/); `Authorization: Bearer
 *    <key>`, standard `{model, messages}` request body.
 *  - **OpenCode** (plain, not Zen) turns out to be the CLI/agent product itself — it is a client
 *    that connects OUT to other providers (including its own Zen gateway), not a separately
 *    documented hosted inference endpoint of its own. No authoritative "OpenCode's own API" docs
 *    were found. Per decision 4, this adapter points the plain "OpenCode" provider at the SAME
 *    `https://opencode.ai/zen/v1` base as Zen (the only real hosted endpoint under that domain) as
 *    the explicit fallback — this one adapter (not the other six) is the one to re-check/adjust if
 *    that guess turns out wrong once a real OpenCode API key is tested against it.
 *
 * Round 2 (Feature 1) — [listModels] hits `GET {baseUrl}/models` (the OpenAI-compatible list
 * endpoint every one of these five providers documents) and classifies each entry as free/paid
 * via [freeClassifier], a per-instance lambda supplied by [AiProviderRegistry] since the five
 * providers sharing this class have very different pricing-exposure shapes:
 *  - **OpenRouter**: `pricing.prompt`/`pricing.completion` are both the string `"0"` for a free
 *    model — this is OpenRouter's own documented convention (openrouter.ai/docs — models list).
 *  - **OpenAI**: no free tier exists; no classifier, every model returned is shown unfiltered.
 *  - **NVIDIA NIM**: the catalog is mostly free-to-try via API credits and the `/v1/models`
 *    response carries no explicit pricing/tier field to filter on — best-effort: no classifier,
 *    every model returned is shown (assumption, flagged for correction).
 *  - **OpenCode Zen / OpenCode**: opencode.ai/docs/zen/ does not expose a machine-readable
 *    pricing field on `/v1/models` as of this check — best-effort: no classifier, every model
 *    shown (assumption, flagged for correction per the plan's "if no pricing data is exposed,
 *    list all with a comment noting the limitation" instruction).
 */
class OpenAiCompatibleProvider(
    override val id: String,
    private val baseUrl: String,
    private val client: OkHttpClient = defaultClient,
    /** Null = no pricing signal available for this provider; every model is returned unfiltered
     *  with `free = null`. Non-null = classify each raw model JSON object; [onlyFree] then decides
     *  whether unfree ones get dropped from the result or just labelled. */
    private val freeClassifier: ((JsonObject) -> Boolean)? = null,
    private val onlyFree: Boolean = false
) : AiProvider {

    @Serializable
    private data class Message(val role: String, val content: String)

    @Serializable
    private data class ChatRequest(val model: String, val messages: List<Message>)

    @Serializable
    private data class Choice(val message: MessageOut? = null)

    @Serializable
    private data class MessageOut(val content: String? = null)

    @Serializable
    private data class ChatResponse(val choices: List<Choice> = emptyList(), val error: ErrorBody? = null)

    @Serializable
    private data class ErrorBody(val message: String? = null)

    override suspend fun complete(apiKey: String, model: String, prompt: String): AiResult =
        chat(apiKey, model, listOf(AiChatMessage(AiChatRole.USER, prompt)))

    override suspend fun chat(apiKey: String, model: String, messages: List<AiChatMessage>): AiResult =
        withContext(Dispatchers.IO) {
            try {
                val body = json.encodeToString(
                    ChatRequest.serializer(),
                    ChatRequest(model = model, messages = messages.map { Message(roleName(it.role), it.content) })
                )
                val request = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(body.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext AiResult.Failure(friendlyHttpError(id, response.code, text))
                    }
                    val parsed = runCatching { json.decodeFromString(ChatResponse.serializer(), text) }.getOrNull()
                    val content = parsed?.choices?.firstOrNull()?.message?.content?.trim()
                    if (content.isNullOrBlank()) {
                        Log.e(TAG, "$id: empty/unparseable response body: $text")
                        AiResult.Failure("$id returned an empty response. Try again in a moment.")
                    } else {
                        AiResult.Success(content)
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "$id: network failure", e)
                AiResult.Failure("Couldn't reach $id. Check your connection and try again.")
            } catch (e: Exception) {
                Log.e(TAG, "$id: unexpected failure", e)
                AiResult.Failure("Something went wrong talking to $id.")
            }
        }

    override suspend fun listModels(apiKey: String): AiModelListResult =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/models")
                    .header("Authorization", "Bearer $apiKey")
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
                        val free = freeClassifier?.invoke(obj)
                        AiModel(id = modelId, free = free)
                    }.sortedBy { it.id }
                    if (models.isEmpty()) {
                        AiModelListResult.Failure("$id didn't return any models for this key.")
                    } else {
                        val result = if (onlyFree) models.filter { it.free == true } else models
                        if (result.isEmpty()) {
                            AiModelListResult.Failure("$id returned models, but none matched the free-tier filter.")
                        } else {
                            AiModelListResult.Success(result)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "$id: network failure listing models", e)
                AiModelListResult.Failure("Couldn't reach $id. Check your connection and try again.")
            } catch (e: Exception) {
                Log.e(TAG, "$id: unexpected failure listing models", e)
                AiModelListResult.Failure("Something went wrong listing $id's models.")
            }
        }

    private fun roleName(role: AiChatRole): String = when (role) {
        AiChatRole.SYSTEM -> "system"
        AiChatRole.USER -> "user"
        AiChatRole.ASSISTANT -> "assistant"
    }

    companion object {
        private const val TAG = "OpenAiCompatibleProvider"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        /** OpenRouter's documented convention: a model is free when both prompt and completion
         *  price strings are `"0"` (openrouter.ai/docs — models list `pricing` object). */
        val openRouterFreeClassifier: (JsonObject) -> Boolean = classifier@{ obj ->
            val pricing = obj["pricing"] as? JsonObject ?: return@classifier false
            val prompt = pricing["prompt"]?.jsonPrimitive?.contentOrNull
            val completion = pricing["completion"]?.jsonPrimitive?.contentOrNull
            prompt == "0" && completion == "0"
        }
    }
}

/** Shared by every adapter — a plain-language sentence per common HTTP status (C9), never a raw
 *  status code or response body surfaced to the UI. */
internal fun friendlyHttpError(providerLabel: String, code: Int, rawBody: String): String {
    android.util.Log.e("AiProvider", "$providerLabel HTTP $code: $rawBody")
    return when (code) {
        401, 403 -> "That key was rejected by $providerLabel."
        404 -> "$providerLabel didn't recognise that model name."
        429 -> "$providerLabel is rate-limiting this key right now. Try again shortly."
        in 500..599 -> "$providerLabel is having trouble right now. Try again later."
        else -> "$providerLabel returned an error (HTTP $code)."
    }
}
