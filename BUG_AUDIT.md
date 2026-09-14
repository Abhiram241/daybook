# Daybook Bug & Silent-Failure Audit — 2026-09-13

Read-only audit. Scope: whole app, with extra scrutiny on the Daily Report tab
(`data/DailyReportRepository.kt`, `ui/report/`) and the AI provider layer (`data/ai/`), per
request. Prior context reviewed: `DAILY_REPORT_PLAN.md`, `CODEBASE_REVIEW.md` was not present in
the repo root (not found by that name — `HEALTH_AND_WORKOUT_PLAN.md`, `HEALTH_VITALS_RICHNESS_PLAN.md`
and the various `*_BUG_REPORT*.md` / `*_FIX_PLAN.md` files were used instead as the record of
already-fixed issues, e.g. `LOGIN_REDESIGN_RISK_FIX_PLAN.md` Phase 0b/10 which hardened
`CloudSyncRepository` and introduced `safeLaunch`/`recordUnhandledException`). Findings below avoid
re-reporting anything that record shows already fixed.

---

## Critical (data loss / crash)

### C1. `DailyReportRepository.observeReport`'s combine chain has no `.catch{}` — violates the codebase's own established Flow-safety convention, and defeats R-DR1
**File:** `app/src/main/java/com/daybook/app/data/DailyReportRepository.kt:135-198`, consumed by
`app/src/main/java/com/daybook/app/ui/report/DailyReportViewModel.kt:80-116` (`reportFlow`, `uiState`)

The four report sections (workout, health, intake, todo) are combined into one Flow with no
per-section isolation and no `.catch{}` anywhere in the chain (confirmed: zero `catch` occurrences
in either file). `HomeViewModel.kt` — the app's own precedent, cited by name in
`util/ViewModelExt.kt`'s KDoc as "the same `.catch{}` shape" every cold Flow pipeline is supposed
to use — wraps essentially every `stateIn`'d flow with `.catch { recordUnhandledException(it) }`
(10 occurrences, e.g. `HomeViewModel.kt:248,310,313,320,328,333,336,343,359`). `DailyReportViewModel`
has none. `safeLaunch` (used elsewhere in the same file for `generate()`/`sendChatMessage()`)
does **not** cover this — it only guards `launch` blocks, not a `stateIn`'d cold Flow, exactly as
`ViewModelExt.kt`'s own KDoc says: "a cold `Flow` pipeline's `stateIn` … see `HomeViewModel`'s
`.catch{}` additions."

**Trigger:** any exception thrown inside `buildWorkoutSection` (e.g. a `WorkoutDao` query failure,
`workoutRepository.resolveExercise` throwing on unexpected data), `visibleHealthCards`, or any of
the underlying Room DAO Flows (`observeSessionsForLocalDate`, `observeDay`,
`getAllOccurrencesInTimeRange`, etc.) propagates uncaught through `combine` → `reportFlow` →
`uiState`'s `stateIn`. Compose's `collectAsStateWithLifecycle` has no exception handler either, so
this reaches the process's default uncaught-exception handler and crashes the app while the user is
just viewing their daily report — for any one of four independent, unrelated data sources having a
bad day. This also means the plan's own R-DR1 promise ("each section is individually
absent-tolerant … a missing section is announced, not omitted without explanation") is not actually
enforced by the code: a bug in the workout section does not degrade to "no workout section shown,"
it crashes the whole page, including health/intake/todo that were otherwise fine.

**Fix direction:** wrap `workoutFlow`, `healthDayFlow`/`healthSessionsFlow`, `intakeFlow`, and
`todoFlow` each in their own `.catch { recordUnhandledException(it); emit(null / emptyList()) }`
before they enter the `combine`, so one section's failure degrades to that section being absent
(matching R-DR1's language) instead of taking down the whole page; keep an outer `.catch{}` on the
final `combine` result as a last-resort net, matching `HomeViewModel`'s pattern exactly.

### C2. `AiKeyStore.loadAll()` is not exception-guarded — a corrupted/undecryptable `EncryptedSharedPreferences` entry crashes on construction
**File:** `app/src/main/java/com/daybook/app/data/ai/AiKeyStore.kt:44-56, 91-105`

`openPrefs()` (the `MasterKey`/`EncryptedSharedPreferences.create()` call) is wrapped in
`runCatching`, falling back to a plaintext file if key/file creation itself fails — a reasonable,
already-considered fallback. But `loadAll()` (called unguarded, both in the constructor at line 46
and after every `setApiKey`/`setModel`) calls `prefs.getString(...)` directly with no
`runCatching`. `EncryptedSharedPreferences.getString()` decrypts each value lazily on read and
throws (`SecurityException`/`GeneralSecurityException`-wrapped `RuntimeException`) if that specific
value can't be decrypted — which is exactly the "corrupted file" / "keystore key invalidated after
a lock-screen change" scenario named in the audit brief: `create()` can succeed (the file opens
fine) while a previously-written value is no longer decryptable because the underlying Keystore key
was invalidated (e.g., biometric enrollment changed, or a factory-key-invalidating OS event) or the
file was partially corrupted/restored from an unrelated device via ADB backup.

**Trigger:** any one provider's stored key becomes undecryptable → the very next `AiKeyStore`
construction (happens whenever `AiProvidersViewModel` or `DailyReportViewModel` is created, i.e.
opening Settings → AI Providers or the Daily Report tab) throws inside the constructor →
Hilt/ViewModel creation fails → that screen crashes outright, with no plain-language message, no
`AiResult.Failure`, nothing — the C9 "never silently drop, always say so" idiom this whole feature
otherwise follows carefully is completely bypassed here because the failure happens before any
`AiResult` machinery even exists.

**Fix direction:** wrap each `prefs.getString(...)` read in `loadAll()` in its own
`runCatching { }.getOrNull()`, treating an undecryptable individual entry the same as "no key
saved" (and ideally logging/recording it via `recordUnhandledException` once) rather than letting
it throw out of the constructor.

---

## High (silent failure / incorrect behavior user can't detect)

### H1. AI key storage silently downgrades from encrypted to plaintext with no user-visible signal
**File:** `app/src/main/java/com/daybook/app/data/ai/AiKeyStore.kt:91-105`

When `EncryptedSharedPreferences.create()` throws, the code falls back to
`context.getSharedPreferences(FILE_FALLBACK, MODE_PRIVATE)` — an **unencrypted** file — logged only
via `Log.w`. The KDoc explicitly frames this as intentional ("the worst case is a weaker at-rest
story, not bricking the app"), and that trade-off is reasonable, but nothing in the Settings →
AI Providers UI ever surfaces "your API keys are currently stored unencrypted" to the user. A
credential silently moving from encrypted to plaintext storage is precisely the kind of
consequential, undisclosed state change C9 says must be surfaced, not just logged.
**Fix direction:** have `AiKeyStore` expose whether it's in fallback mode (a `StateFlow<Boolean>`
or similar) and have the AI Providers settings screen show a one-line warning banner when true.

### H2. `generate()` has no re-entrancy guard, unlike `sendChatMessage()`
**File:** `app/src/main/java/com/daybook/app/ui/report/DailyReportViewModel.kt:135-186` vs.
`253-283`; UI gate at `app/src/main/java/com/daybook/app/ui/report/DailyReportScreen.kt:392-398`

`sendChatMessage()` explicitly guards against double-submit: `if (draft.isBlank() ||
_chatSending.value) return` (line 255). `generate()` has no equivalent check against
`_isGenerating.value` — it relies entirely on the Compose button's `enabled = !state.isGenerating`
(`DailyReportScreen.kt:397`). Because `state` is a `StateFlow` snapshot taken via
`collectAsStateWithLifecycle`, there is a real (if short) window between two rapid taps before
recomposition disables the button, in which `generate()` can be invoked twice, launching two
concurrent `safeLaunch` blocks that both call `AiProviderRegistry.forId(provider).complete(...)`
and both eventually call `repository.saveAiSummary(...)` for the same `localDate` (the table's
primary key) — the DAO's `upsert` means one silently overwrites the other, and the user has paid
for/waited on two API calls for one visible result, with no indication a race occurred.
**Fix direction:** add the same `if (_isGenerating.value) return` guard at the top of `generate()`
that `sendChatMessage()` already has for `_chatSending.value`.

### H3. Chat conversation is lost with zero indication on process death
**File:** `app/src/main/java/com/daybook/app/ui/report/DailyReportViewModel.kt:195-208`

Per the code's own comment (lines 189-193), chat history is intentionally "in-memory only for the
session," held in plain `MutableStateFlow` fields with no `SavedStateHandle` backing. This
survives a configuration change (the `ViewModel` instance itself survives rotation) but **not**
process death — if Android kills the app in the background (a very ordinary event on a
memory-constrained device, or the OS reclaiming the process while the user reads a notification)
and the user returns, a fresh `DailyReportViewModel` is created, `_chatMessages`/`_chatOpen` reset
to their defaults, and the entire conversation — including a still-open chat sheet — disappears
with the screen simply rendering as if `Chat with AI about today` had never been tapped. Nothing
tells the user their conversation is gone; it just isn't there anymore.
**Fix direction:** either persist the transcript through a `SavedStateHandle` (acceptable for
short-lived text) or, at minimum, detect the "chat sheet was open but is now empty" case on
recreation and show a one-line "Your chat session ended" notice instead of silently reopening
empty.

### H4. `testKey()` has no re-entrancy guard against a double "Test" tap on the same provider
**File:** `app/src/main/java/com/daybook/app/ui/settings/AiProvidersViewModel.kt:64-92`

Similar shape to H2: two rapid taps on the same provider's "Test" action launch two concurrent
`safeLaunch` blocks sharing the same `_testingProvider`/`_testResults`/`_modelLists` state keyed by
`id`. Whichever network call finishes first sets `_testingProvider.value = null`, so the UI can
briefly show "not testing" while the second call is still in flight, and then flip back to a result
when it lands — a confusing but not data-destructive state flicker (lower stakes than H2 since nothing
is persisted here, hence High rather than Critical).
**Fix direction:** guard entry with `if (_testingProvider.value == id) return`.

---

## Medium (UX / state bug)

### M1. `MIGRATION_25_26`'s nav-tab column update depends on `nav_tabs` never being NULL — true today, but undocumented as a hard invariant
**File:** `app/src/main/java/com/daybook/app/data/local/Migrations.kt:776-789`

`UPDATE app_settings SET nav_tabs = nav_tabs || ',report' WHERE nav_tabs NOT LIKE '%report%'` relies
on SQLite's `||` returning NULL if either operand is NULL, which would silently leave that row's
`nav_tabs` as NULL instead of appending `,report`. The column does carry `NOT NULL DEFAULT
'home,routines,foodmed'` (confirmed at `Migrations.kt:382` and `:721`), so this is not currently
reachable — noted as a Medium (not Low) only because it's a silent-failure *pattern* (an unguarded
NULL-propagating concatenation) worth flagging rather than an active bug; if any future migration
ever ALTERs that column to be nullable, this statement would start silently corrupting the nav bar
for affected rows with no error surfaced anywhere.
**Fix direction:** use `COALESCE(nav_tabs, 'home,routines,foodmed') || ',report'` defensively, which
costs nothing today and removes the latent trap.

### M2. Google AI Studio's free-tier filter can silently produce a different result set than intended by the fallback logic
**File:** `app/src/main/java/com/daybook/app/data/ai/GoogleAiStudioProvider.kt:140-143`

`listModels` filters to `free == true` first, and only falls back to the unfiltered list if the
free-filtered list is empty. Given `FREE_TIER_PATTERN = Regex("flash")` (a bare substring match,
line 158), a model whose name contains "flash" anywhere (not necessarily indicating Google's actual
free tier — e.g. a hypothetical future paid "ultra-flash" tier) would be silently included with no
way for the user to see the non-flash paid models at all in that case, since the fallback path never
triggers while at least one "flash"-matching id exists. This is documented in the adapter's own KDoc
as a known assumption/best-effort pattern, so it's not a fresh bug, but the silent
all-or-nothing behavior (either every flash model, or if none exist every model, never "everything
with a per-model free/paid label") means a user genuinely cannot see the boundary the code is
applying.
**Fix direction:** always return every model annotated with its computed `free` flag (as
`AiModel(free = …)` already supports) and let the UI decide how to group/label free vs. not, rather
than the provider layer unilaterally dropping non-matching entries.

### M3. `sendChatMessage()` permanently disables sending if `state.selectedModel` is blank, with no error shown
**File:** `app/src/main/java/com/daybook/app/ui/report/DailyReportViewModel.kt:253-283`

`sendChatMessage()` reads `model = state.selectedModel.trim()` but never checks it for blankness
before calling `AiProviderRegistry.forId(provider).chat(apiKey, model, history)` (contrast with
`generate()`, which explicitly checks `model.isBlank()` at line 144 and surfaces
"Enter a model name for X in Settings → AI Providers"). If a provider has a saved key but no model
string (a legitimate persisted state — `AiKeyStore` allows an empty model), `openChat()`'s own guard
(line 221-224) does catch this before the chat sheet even opens, but `sendChatMessage()` itself has
no equivalent second check — should `state.selectedModel` become blank again after the sheet is
already open (e.g., the user clears the model field in Settings in another tab while the chat sheet
is open, since `_selectedModel` in the VM is a separate manually-set field, not tied live to the
key-store's model unless untouched), a subsequent send goes to the provider adapter with an empty
`model` string, which every adapter will simply forward as `"model": ""` in the request body,
producing a provider-side 400 that surfaces via `friendlyHttpError` as a generic
"provider returned an error" rather than the specific, actionable "enter a model name" message.
**Fix direction:** add the same blank-model check `generate()` has, at the top of
`sendChatMessage()`.

---

## Low (code smell / minor risk)

### L1. `OpenAiCompatibleProvider`/`GoogleAiStudioProvider`/`AnthropicProvider` all construct a `Json { ignoreUnknownKeys = true }` but do not set `isLenient` or wrap the top-level `parseToJsonElement`/`decodeFromString` consistently
Every adapter's `runCatching { json.decodeFromString(...) }.getOrNull()` (e.g.
`OpenAiCompatibleProvider.kt:100`) is good practice and already guards against a malformed shape —
this is confirmed **solid**, not a bug (see "Already Solid" below). The only minor smell: each
adapter re-declares its own private `Json` instance and TAG rather than sharing one, a pure
duplication/maintenance nit, not a functional issue.

### L2. `AiProvidersViewModel.setApiKey` clears `_modelLists` synchronously but the underlying key write is asynchronous
**File:** `app/src/main/java/com/daybook/app/ui/settings/AiProvidersViewModel.kt:38-42`

`setApiKey` launches the actual write via `safeLaunch { keyStore.setApiKey(id, apiKey) }` (async)
but clears `_modelLists.value = _modelLists.value - id` synchronously, outside that coroutine. This
ordering happens to be harmless (clearing early is if anything safer), but it's a small inconsistency
worth a comment: nothing prevents a `testKey()` call from racing in between the synchronous clear and
the eventual async key persistence, using the *old* key value being replaced. Cosmetic risk only.

---

## Already-solid (checked, no issue found)

- **Every AI provider adapter's network path** (`OpenAiCompatibleProvider`, `GoogleAiStudioProvider`,
  `AnthropicProvider`) has explicit connect/read/write timeouts on the shared `OkHttpClient`
  (`OpenAiCompatibleProvider.kt:171-177`, 30s/60s/30s), catches `IOException` and generic
  `Exception` separately with distinct plain-language messages, and defensively `runCatching`s
  every JSON parse with a `getOrNull()` fallback to a friendly "empty/unparseable response"
  message rather than letting a malformed provider response crash the call — this is exactly the
  "degrade gracefully on unexpected API shape" behavior the audit asked about, and it's implemented
  correctly and consistently across all three adapter classes.
- **`CloudSyncRepository`'s snapshot listeners** (`attachSnapshotListeners`,
  `scopedMonthsListener`, `CloudSyncRepository.kt:848-918`) were audited for the
  double-arm/duplicate-processing risk the brief flagged — both listener callbacks are already
  `runCatching`-wrapped with explicit comments (`LOGIN_REDESIGN_RISK_FIX_PLAN.md Phase 0b`) noting
  they were previously the two unguarded `scope.launch` call sites and were fixed; no regression
  found in the current code, and `onLocalDataReplaced` (`:830-844`) correctly unions rather than
  replaces `hydratedMonths` to avoid the specific spurious-deletion bug (S5/S-13) its own comment
  describes.
- **`DailyReportRepository`'s `JournalQa.decode`** (reused from `ExportImportRepository.kt:1410-1415`
  for both intake and todo Q&A rows) already wraps its JSON decode in
  `runCatching { }.getOrDefault(emptyList())` — malformed `qaJson` in the DB degrades to an empty
  Q&A list rather than throwing, so this was ruled out as the source of C1's crash risk (the crash
  risk in C1 is architectural — no `.catch{}` on the Flow chain overall — not this specific call).

---

## Summary

| Severity | Count |
|---|---|
| Critical | 2 |
| High | 4 |
| Medium | 3 |
| Low | 2 |

**Total: 11 findings.**

**Single most important finding:** **C1** — the Daily Report screen's entire four-section data
pipeline (`DailyReportRepository.observeReport` → `DailyReportViewModel.uiState`) has no `.catch{}`
anywhere, unlike every comparable Flow in `HomeViewModel`, which the codebase's own
`util/ViewModelExt.kt` documents as the required pattern for exactly this situation. This both
crashes the whole Daily Report tab on any single section's failure (a DB hiccup in Workout, Health,
Intake, or Habits alike) and quietly defeats the plan's headline R-DR1 promise that each of the four
sections degrades independently — the newest tab in the app is the one place that promise isn't
actually enforced in code.
