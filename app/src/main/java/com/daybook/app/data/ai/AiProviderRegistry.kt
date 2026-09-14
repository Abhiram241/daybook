package com.daybook.app.data.ai

/**
 * DAILY_REPORT_PLAN.md §3.3 — one [AiProvider] instance per [AiProviderId]. `OpenAiCompatibleProvider`
 * is parameterised by base URL and reused for five of the seven providers; Google AI Studio and
 * Anthropic get their own adapters (§3.3).
 */
object AiProviderRegistry {

    private val instances: Map<AiProviderId, AiProvider> = mapOf(
        // OpenAI has no free tier — listModels returns everything unfiltered (Feature 1).
        AiProviderId.OPENAI to OpenAiCompatibleProvider("OpenAI", "https://api.openai.com/v1"),
        // OpenRouter: filtered to pricing.prompt == pricing.completion == "0" — see
        // OpenAiCompatibleProvider.openRouterFreeClassifier's KDoc for the source.
        AiProviderId.OPENROUTER to OpenAiCompatibleProvider(
            "OpenRouter", "https://openrouter.ai/api/v1",
            freeClassifier = OpenAiCompatibleProvider.openRouterFreeClassifier,
            onlyFree = true
        ),
        // NVIDIA NIM: no explicit pricing/tier field in /v1/models — best-effort, lists everything
        // unfiltered per the plan's "mostly free-to-try via API credits" assumption.
        AiProviderId.NVIDIA_NIM to OpenAiCompatibleProvider("NVIDIA NIM", "https://integrate.api.nvidia.com/v1"),
        // D0 — confirmed OpenAI-compatible (opencode.ai/docs/zen/). No machine-readable pricing
        // field found on /v1/models as of this check — best-effort, lists everything unfiltered.
        AiProviderId.OPENCODE_ZEN to OpenAiCompatibleProvider("OpenCode Zen", "https://opencode.ai/zen/v1"),
        // D0 — UNCONFIRMED: "OpenCode" itself has no documented hosted inference API of its own
        // (it's the CLI/agent product, a client of other providers including its own Zen gateway).
        // Falls back to the same Zen-compatible base per decision 4; this is the one adapter to
        // re-check against a real OpenCode key.
        AiProviderId.OPENCODE to OpenAiCompatibleProvider("OpenCode", "https://opencode.ai/zen/v1"),
        AiProviderId.GOOGLE_AI_STUDIO to GoogleAiStudioProvider(),
        AiProviderId.ANTHROPIC to AnthropicProvider()
    )

    fun forId(id: AiProviderId): AiProvider = instances.getValue(id)
}
