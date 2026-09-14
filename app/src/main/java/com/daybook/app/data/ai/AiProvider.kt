package com.daybook.app.data.ai

/**
 * DAILY_REPORT_PLAN.md §3.3 — one small interface every adapter implements. A synchronous,
 * user-triggered suspend call only — no background worker, no retry loop, no polling (C7).
 */
interface AiProvider {
    val id: String
    suspend fun complete(apiKey: String, model: String, prompt: String): AiResult

    /**
     * Round 2 (Feature 1) — lists the models this key can see, so Settings can offer a picker
     * instead of a free-text field. Per-provider free-tier filtering is applied inside the
     * implementation (see each adapter's KDoc for the exact rule and its source/assumption).
     */
    suspend fun listModels(apiKey: String): AiModelListResult

    /**
     * Round 2 (Feature 4) — true multi-turn chat, used by the Daily Report "Chat" entry point.
     * [messages] is the whole conversation so far including the leading context/system message;
     * each adapter maps [AiChatRole] onto whatever shape its own API expects (a separate
     * top-level `system` field for Anthropic, `systemInstruction` for Google, a `system` row in
     * the `messages` array for the OpenAI-compatible five).
     */
    suspend fun chat(apiKey: String, model: String, messages: List<AiChatMessage>): AiResult
}

/** A failure is always a plain-language sentence (C9) — the raw exception/HTTP body goes to
 *  `Log.e`, never straight to the UI. */
sealed class AiResult {
    data class Success(val text: String) : AiResult()
    data class Failure(val message: String) : AiResult()
}

/** Round 2 (Feature 1) — one model as returned by a provider's list-models endpoint.
 *  [free] is `true`/`false` when the adapter could determine it, `null` when unknown (the
 *  provider exposes no pricing/tier signal, e.g. NVIDIA NIM / OpenCode Zen best-effort cases). */
data class AiModel(val id: String, val free: Boolean? = null)

sealed class AiModelListResult {
    data class Success(val models: List<AiModel>) : AiModelListResult()
    data class Failure(val message: String) : AiModelListResult()
}

/** Round 2 (Feature 4) — one turn in a chat conversation. SYSTEM is always the first message
 *  (the injected daily-report context); adapters that don't support inline system turns lift it
 *  out into their own dedicated field. */
enum class AiChatRole { SYSTEM, USER, ASSISTANT }

data class AiChatMessage(val role: AiChatRole, val content: String)

/**
 * §3.2 — the seven providers. Kept as its own enum (not just a raw String) so every call site —
 * the settings rows, the key store, the provider picker — enumerates the same fixed list in the
 * same order, with one canonical id/label/placeholder-model per provider.
 */
enum class AiProviderId(
    val label: String,
    /** Shown as a field hint only — never silently substituted if the model field is left blank
     *  (§3.2: a blank model at generation time is a validation error, not a silent fallback). */
    val modelPlaceholder: String
) {
    GOOGLE_AI_STUDIO("Google AI Studio (Gemini)", "gemini-2.0-flash"),
    OPENROUTER("OpenRouter", "openai/gpt-4o-mini"),
    NVIDIA_NIM("NVIDIA NIM", "meta/llama-3.1-8b-instruct"),
    OPENCODE("OpenCode", "claude-3-5-sonnet"),
    OPENCODE_ZEN("OpenCode Zen", "grok-code"),
    OPENAI("OpenAI", "gpt-4o-mini"),
    ANTHROPIC("Anthropic (Claude)", "claude-3-5-haiku-20241022");

    companion object {
        fun fromNameOrNull(name: String?): AiProviderId? = entries.firstOrNull { it.name == name }
    }
}
