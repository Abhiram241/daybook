package com.daybook.app.data.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** DAILY_REPORT_PLAN.md §3.3 — Gemini's `generateContent` REST shape; genuinely different request
 *  JSON from the OpenAI-compatible five, so its own adapter. The key travels as a query param
 *  (`?key=`), not a bearer header — Google AI Studio's documented auth style.
 *
 *  Round 2 (Feature 4) — real multi-turn: `contents` is an array of `{role, parts}` where role is
 *  `"user"`/`"model"` (Gemini's own vocabulary, not "assistant"); a SYSTEM-role turn is lifted out
 *  into the separate `systemInstruction` field `generateContent` documents, mirroring Anthropic's
 *  `system` field. */
class GoogleAiStudioProvider(
    private val client: OkHttpClient = OpenAiCompatibleProvider.defaultClient
) : AiProvider {

    override val id: String = "Google AI Studio"

    @Serializable
    private data class Part(val text: String)

    @Serializable
    private data class Content(val role: String? = null, val parts: List<Part>)

    @Serializable
    private data class GenerateRequest(val contents: List<Content>, val systemInstruction: Content? = null)

    @Serializable
    private data class Candidate(val content: ContentOut? = null)

    @Serializable
    private data class ContentOut(val parts: List<PartOut> = emptyList())

    @Serializable
    private data class PartOut(val text: String? = null)

    @Serializable
    private data class GenerateResponse(val candidates: List<Candidate> = emptyList())

    override suspend fun complete(apiKey: String, model: String, prompt: String): AiResult =
        chat(apiKey, model, listOf(AiChatMessage(AiChatRole.USER, prompt)))

    override suspend fun chat(apiKey: String, model: String, messages: List<AiChatMessage>): AiResult =
        withContext(Dispatchers.IO) {
            try {
                val systemText = messages.filter { it.role == AiChatRole.SYSTEM }
                    .joinToString("\n\n") { it.content }
                    .takeIf { it.isNotBlank() }
                val contents = messages.filter { it.role != AiChatRole.SYSTEM }.map {
                    Content(
                        role = if (it.role == AiChatRole.ASSISTANT) "model" else "user",
                        parts = listOf(Part(it.content))
                    )
                }
                val body = json.encodeToString(
                    GenerateRequest.serializer(),
                    GenerateRequest(
                        contents = contents,
                        systemInstruction = systemText?.let { Content(parts = listOf(Part(it))) }
                    )
                )
                val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "$model:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .post(body.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext AiResult.Failure(friendlyHttpError(id, response.code, text))
                    }
                    val parsed = runCatching { json.decodeFromString(GenerateResponse.serializer(), text) }.getOrNull()
                    val content = parsed?.candidates?.firstOrNull()?.content?.parts?.joinToString("") { it.text.orEmpty() }?.trim()
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

    /**
     * Round 2 (Feature 1) — `GET /v1beta/models?key=`, filtered to Google's documented free-tier
     * family. Source/assumption (Sept 2026): Google AI Studio's free tier is documented as the
     * `gemini-*-flash*` and `gemini-*-flash-lite*` model family; the list endpoint itself carries
     * no explicit price/tier field to read instead, so this is a name-pattern allowlist rather
     * than a value read off the response — flagged here so it can be corrected if Google adds a
     * real tier field or changes which models are free later. Also drops embedding/vision-only
     * models that don't support `generateContent` (checked via `supportedGenerationMethods`).
     */
    override suspend fun listModels(apiKey: String): AiModelListResult =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
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
                    val dataArray = (root["models"] as? kotlinx.serialization.json.JsonArray).orEmpty()
                    val models = dataArray.mapNotNull { el ->
                        val obj = el as? JsonObject ?: return@mapNotNull null
                        val rawName = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        val modelId = rawName.removePrefix("models/")
                        val methods = obj["supportedGenerationMethods"]?.jsonArray
                            ?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
                        if (methods.isNotEmpty() && "generateContent" !in methods) return@mapNotNull null
                        AiModel(id = modelId, free = FREE_TIER_PATTERN.containsMatchIn(modelId))
                    }.sortedBy { it.id }
                    // M2 — return every model annotated with its computed `free` flag rather than
                    // silently dropping non-matching entries; the UI (the model picker's
                    // Free/Paid label) decides how to group/label them.
                    if (models.isNotEmpty()) AiModelListResult.Success(models)
                    else AiModelListResult.Failure("$id didn't return any models for this key.")
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
        const val TAG = "GoogleAiStudioProvider"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val json = Json { ignoreUnknownKeys = true }
        val FREE_TIER_PATTERN = Regex("flash")
    }
}
