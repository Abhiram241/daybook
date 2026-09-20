package com.daybook.app.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daybook.app.data.AppSettingsRepository
import com.daybook.app.data.CatalogExercise
import com.daybook.app.data.WorkoutPhotoStore
import com.daybook.app.data.WorkoutRepository
import com.daybook.app.data.model.WorkoutExercise
import com.daybook.app.data.model.WorkoutSession
import com.daybook.app.data.model.WorkoutSessionPhoto
import com.daybook.app.data.model.WorkoutSet
import com.daybook.app.data.workout.Equipment
import com.daybook.app.data.workout.MuscleGroup
import com.daybook.app.data.workout.SessionStats
import com.daybook.app.data.workout.RestTimerPrefs
import com.daybook.app.data.workout.sessionElapsedSeconds
import com.daybook.app.data.workout.WeightUnit
import com.daybook.app.data.workout.parseWeightUnit
import com.daybook.app.data.workout.sessionStats
import com.daybook.app.util.notification.NotificationUtils
import com.daybook.app.util.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** One exercise block, everything the set table needs, immutable primitives only (P5). */
data class BlockUi(
    val block: WorkoutExercise,
    val exerciseName: String,
    val trackingMode: String,
    // Bug fix (post-A6) — carried through so the block header can show the real RepDB
    // illustration (`ExerciseThumbnail`) instead of the generic taxonomy icon.
    val imageId: String? = null,
    val hasStartPeak: Boolean = false,
    // BEAST_MODE_REDESIGN_PLAN.md §3.7 — carried through so the block card can tint itself by
    // muscle group instead of always CardTints.Neutral.
    val primaryMuscle: MuscleGroup = MuscleGroup.OTHER,
    // Item 3 (Beast Mode round 2026-09-16) — carried through for `ExerciseThumbnail`'s equipment
    // fallback, the one axis of the picture chain this block didn't already have.
    val equipment: Equipment = Equipment.OTHER,
    val sets: List<WorkoutSet>,
    val previous: Map<Int, WorkoutSet>,
    val best: WorkoutSet?
)

data class SessionUiState(
    val session: WorkoutSession? = null,
    val blocks: List<BlockUi> = emptyList(),
    val stats: SessionStats = SessionStats(0f, 0)
)

/** A6b (§3.7.1/§3.7.2) — the live log. Owns the elapsed-time ticker and the rest-timer ticker,
 *  the per-block PREVIOUS map and pre-session bests, sessionStats, and consumes
 *  "picked_exercise_id" from the picker's SavedStateHandle result. */
@HiltViewModel
class WorkoutSessionViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    appSettingsRepository: AppSettingsRepository,
    // Item 4 (BEAST_MODE_ROUND_2026-09-16_PLAN.md) — arms/cancels the rest-timer alarm alongside
    // this ViewModel's own in-memory countdown state.
    private val notificationUtils: NotificationUtils,
    private val restTimerPrefs: RestTimerPrefs,
    // Item 12 — session photos.
    private val workoutPhotoStore: WorkoutPhotoStore,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    // ------------------------------------------------------------------------ session photos (item 12)

    val sessionPhotos: kotlinx.coroutines.flow.StateFlow<List<WorkoutSessionPhoto>> =
        repo.observePhotosForSession(sessionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The capture-in-progress file's absolute path, in `SavedStateHandle` (not `remember`) —
     *  the stock camera is a separate activity and can kill this process while it's in the
     *  foreground; the path must survive that to know which file to persist or discard when the
     *  result comes back. */
    private var pendingCaptureFilePath: String?
        get() = savedStateHandle[KEY_PENDING_CAPTURE_PATH]
        set(v) { savedStateHandle[KEY_PENDING_CAPTURE_PATH] = v }

    /** Creates a fresh capture target and returns the `content://…fileprovider` Uri to launch
     *  `ActivityResultContracts.TakePicture()` with. */
    fun preparePendingCapture(): android.net.Uri {
        val file = workoutPhotoStore.newCaptureFile()
        pendingCaptureFilePath = file.absolutePath
        return workoutPhotoStore.uriFor(file)
    }

    /** The camera activity returned. [success] mirrors `TakePicture()`'s own result contract. */
    fun onCaptureResult(success: Boolean) = safeLaunch {
        val path = pendingCaptureFilePath ?: return@safeLaunch
        pendingCaptureFilePath = null
        val file = java.io.File(path)
        if (success) {
            val persistedPath = workoutPhotoStore.persistCapturedFile(file)
            if (persistedPath != null) repo.addSessionPhoto(sessionId, persistedPath)
        } else {
            workoutPhotoStore.discardCaptureFile(file)
        }
    }

    fun removePhoto(photo: WorkoutSessionPhoto) = safeLaunch {
        repo.deleteSessionPhoto(photo.id, photo.filePath)
    }

    /** §2.7 fix — the Volume tile/set-table used to hardcode "kg" regardless of
     *  `app_settings.weight_unit`; now the same read-through `WorkoutDetailViewModel` already
     *  used. */
    val weightUnit = appSettingsRepository.observeSettings()
        .map { parseWeightUnit(it.weightUnit) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUnit.KG)

    // P1: no `List<WorkoutSet>` of the VM's own — everything below is a read-through of Room.
    private val base = combine(
        repo.observeSession(sessionId),
        repo.observeExercisesForSession(sessionId),
        repo.observeSetsForSession(sessionId)
    ) { session, exercises, sets -> Triple(session, exercises, sets) }

    private val _derived = MutableStateFlow<Map<String, Pair<Map<Int, WorkoutSet>, WorkoutSet?>>>(emptyMap())
    // Bug fix (post-A6) — the full resolved CatalogExercise (name, trackingMode, imageId,
    // hasStartPeak), not just a name/mode pair, so the block header can render the real RepDB
    // thumbnail via ExerciseThumbnail.
    private val _names = MutableStateFlow<Map<String, CatalogExercise>>(emptyMap())

    val state = combine(base, _derived, _names) { (session, exercises, sets), derived, names ->
        val blocks = exercises.sortedBy { it.orderIndex }.map { block ->
            val resolved = names[block.exerciseId]
            val (prev, best) = derived[block.exerciseId] ?: (emptyMap<Int, WorkoutSet>() to null)
            BlockUi(
                block = block,
                exerciseName = resolved?.name ?: "Exercise",
                trackingMode = resolved?.trackingMode ?: "WEIGHT_REPS",
                imageId = resolved?.imageId,
                hasStartPeak = resolved?.hasStartPeak ?: false,
                primaryMuscle = resolved?.primaryMuscle ?: MuscleGroup.OTHER,
                equipment = resolved?.equipment ?: Equipment.OTHER,
                sets = sets.filter { it.workoutExerciseId == block.id }.sortedBy { it.setNumber },
                previous = prev, best = best
            )
        }
        SessionUiState(session, blocks, sessionStats(sets))
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionUiState())

    init {
        // Refresh the derived per-exercise data (names, PREVIOUS, best) whenever the block set
        // changes — P3: read once per exercise block opened, not per keystroke.
        base.onEach { (_, exercises, _) ->
            val ids = exercises.map { it.exerciseId }.distinct()
            val missingNames = ids.filterNot { it in _names.value }
            if (missingNames.isNotEmpty()) {
                val resolved = missingNames.mapNotNull { id -> repo.resolveExercise(id)?.let { id to it } }.toMap()
                _names.update { current -> current + resolved }
            }
            val missingDerived = ids.filterNot { it in _derived.value }
            if (missingDerived.isNotEmpty()) {
                val computed = missingDerived.associateWith { id ->
                    repo.previousSetsForExercise(id, sessionId) to repo.bestSetForExercise(id, sessionId)
                }
                _derived.update { it + computed }
            }
        }.launchIn(viewModelScope)
    }

    // ------------------------------------------------------------------------ ticker (§3.7.2)
    /**
     * Item 8 / battery audit B-5 (Beast Mode round 2026-09-16) — this used to be a
     * `MutableStateFlow` fed by an unconditional `while (true) { … delay(1000) }` coroutine
     * launched in `init`, which ticked once a second for the ViewModel's ENTIRE lifetime: while
     * the app was backgrounded, and even when there was no session to time. It is now derived from
     * `session.startedAt` exactly the way [restRemainingSeconds] already is (the "L3 fix" below) —
     * the per-second coroutine only exists while a started session is being observed, and
     * `WhileSubscribed(5_000)` stops it ~5s after the screen stops being visible.
     *
     * [[appforfood-workout-feedback]] — this label must stay a LIVE ticker, not a frozen snapshot.
     * It does: the value is always recomputed from `startedAt` (never accumulated), so returning
     * to the screen after any amount of time immediately shows the real elapsed time and then
     * keeps counting. Do not collapse this to a one-shot read.
     *
     * Owner report (2026-09-20) — that rule only ever applied to a still-ACTIVE session. Opening a
     * COMPLETED session via "Edit" reused this same flow, so it kept ticking off the ORIGINAL
     * `startedAt` against `now`, showing wall-clock time since the session ended (sometimes days)
     * instead of the session's actual duration. Now keyed off `(startedAt, endedAt)`: `endedAt !=
     * null` emits the fixed `sessionElapsedSeconds` once and stops; `endedAt == null` keeps the
     * exact live-ticking behaviour above, unchanged.
     */
    val elapsedSeconds: kotlinx.coroutines.flow.StateFlow<Long> = state
        .map { it.session?.let { s -> s.startedAt to s.endedAt } }
        .distinctUntilChanged()
        .flatMapLatest { pair ->
            if (pair == null) {
                kotlinx.coroutines.flow.flowOf(0L)
            } else {
                val (startedAt, endedAt) = pair
                if (endedAt != null) {
                    kotlinx.coroutines.flow.flowOf(
                        sessionElapsedSeconds(startedAt, endedAt, System.currentTimeMillis())
                    )
                } else {
                    kotlinx.coroutines.flow.flow {
                        while (true) {
                            emit(sessionElapsedSeconds(startedAt, null, System.currentTimeMillis()))
                            kotlinx.coroutines.delay(1000)
                        }
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    // ------------------------------------------------------------------------ rest timer (§3.7.2)
    // §2.9/§3 fix — `restEndsAt` used to also be exposed publicly, but nothing ever collected it
    // (only the derived `restRemainingSeconds` below is); `restRemainingSeconds` makes it fully
    // redundant as a public surface, so only the private backing field remains.
    private val _restEndsAt = MutableStateFlow<Long?>(null)

    /** Item 8 — the rest ring needs the timer's total to draw a draining arc, which
     *  [restRemainingSeconds] alone (a bare `Long?`) can't supply. A parallel flow rather than
     *  folding a pair into `_restEndsAt`, so that flow's `flatMapLatest` keeps its shape. Null
     *  exactly when no rest timer is running, so the ring can grey itself out. */
    private val _restTotalSeconds = MutableStateFlow<Int?>(null)
    val restTotalSeconds = _restTotalSeconds.asStateFlow()

    // L3 fix — this used to be a `MutableStateFlow` fed by an unconditional `while(true)` 1Hz
    // ticker that ran for the ViewModel's entire lifetime regardless of whether a rest timer was
    // even running (recomputing `null` once a second forever the rest of the time). Now a flow
    // DERIVED from `_restEndsAt` via `flatMapLatest`: the actual per-second ticking coroutine only
    // exists while a rest timer is active, and is cancelled the instant `_restEndsAt` changes
    // (a new rest started, `cancelRest()`, or this loop's own M7 expiry-clear below).
    val restRemainingSeconds: kotlinx.coroutines.flow.StateFlow<Long?> = _restEndsAt.flatMapLatest { ends ->
        if (ends == null) {
            kotlinx.coroutines.flow.flowOf<Long?>(null)
        } else {
            kotlinx.coroutines.flow.flow {
                while (true) {
                    val remaining = ((ends - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                    emit(remaining)
                    if (remaining <= 0L) {
                        // M7 fix — this never nulled `_restEndsAt` once the countdown hit zero, so
                        // the "Rest over" pill (visibility keyed on `restRemaining != null`) stuck
                        // on the stats card permanently, through every subsequent set, until the
                        // user re-opened the rest sheet and picked "Off". Hold "Rest over" for a
                        // few seconds (so the expiry is still noticeable) then clear it.
                        kotlinx.coroutines.delay(REST_OVER_HOLD_MILLIS)
                        // Item 8 — the ring's total goes with it, so the reserved ring returns to
                        // its inactive (grey, "–") state rather than keeping a stale arc.
                        _restTotalSeconds.value = null
                        _restEndsAt.value = null
                        // Item 4 — the alarm has already fired by the time `remaining` reaches 0
                        // (nothing to cancel there), but the persisted countdown must still be
                        // cleared — otherwise a process restart a moment later would try to
                        // restore an already-expired timer (harmless on its own, `restore()`
                        // refuses anything past its `endsAt`, but there's no reason to leave it).
                        restTimerPrefs.clear()
                        break
                    }
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Item 4 (plan open question 6, taken per its own recommendation) — restore the on-screen
        // countdown if the process was killed and relaunched mid-rest. The ALARM survives this by
        // itself (the OS holds the PendingIntent regardless of whether this ViewModel exists) —
        // this only re-syncs the ring so it doesn't silently show "idle" while a real alarm is
        // still armed underneath it. `restore` already refuses a persisted timer for a different
        // session, or one that has already passed, so no extra guard is needed here, and it
        // deliberately does NOT call `scheduleRestAlarm` again — the alarm this is restoring FROM
        // is still the one armed, re-arming it would just be a harmless but pointless duplicate
        // `FLAG_UPDATE_CURRENT` write.
        restTimerPrefs.restore(sessionId)?.let { (endsAt, totalSeconds) ->
            _restTotalSeconds.value = totalSeconds
            _restEndsAt.value = endsAt
        }
    }

    /** Item 4 — also arms the OS-held alarm (survives backgrounding/locking/process death) and
     *  persists the countdown so it can be restored on relaunch (see the `init` block above). A
     *  new rest replaces any already-armed one automatically (`scheduleRestAlarm`'s
     *  `FLAG_UPDATE_CURRENT`), matching the pre-existing in-memory replace-on-restart behaviour. */
    fun startRest(seconds: Int) {
        val endsAt = System.currentTimeMillis() + seconds * 1000L
        _restTotalSeconds.value = seconds
        _restEndsAt.value = endsAt
        notificationUtils.scheduleRestAlarm(endsAt)
        restTimerPrefs.save(sessionId, endsAt, seconds)
    }

    /** Item 4 — the cancellation matrix: called directly from the rest sheet's "Off" and from a
     *  set being un-completed (`WorkoutSessionScreen`'s `onToggleComplete`), and now also from
     *  every place below that ends the session or invalidates its sets (`finish`, `discard`,
     *  `deleteSet`) — every one of those used to be harmless (the timer was pure in-memory state
     *  that just vanished with the ViewModel); now that a real OS alarm backs it, each is a leak
     *  (a beep with nothing left on screen to make sense of it) unless explicitly cancelled here.
     *  Deliberately NOT called from `onCleared()`: the alarm is meant to survive navigating away
     *  from (or backgrounding, or even process-death during) a still-ACTIVE, unfinished session —
     *  that survival is the entire point of item 4. Only the session genuinely ending, or the rest
     *  being genuinely dismissed, should silence it. */
    fun cancelRest() {
        _restTotalSeconds.value = null
        _restEndsAt.value = null
        notificationUtils.cancelRestAlarm()
        restTimerPrefs.clear()
    }

    /** Item 4 — called from `WorkoutSessionScreen`'s `LifecycleResumeEffect` so a "Rest over"
     *  notification doesn't linger once the user is back looking at the ring it duplicates. Plain
     *  `cancelNotification`, not `cancelRest()` — coming back to the screen must not also kill a
     *  DIFFERENT, still-running rest (e.g. the user backgrounded the app, it beeped, they came
     *  back, started a NEW rest, then briefly switched away and back again). */
    fun dismissRestNotification() {
        notificationUtils.cancelNotification(NotificationUtils.REST_NOTIFICATION_ID)
    }

    // Bug fix (post-A6) — the exercise picker's result used to be consumed here via this
    // screen's own injected SavedStateHandle (per §3.6.6). On-device testing found that path
    // unreliable (tapping a row popped back to the session with nothing added); MainActivity's
    // `composable(SESSION)` now calls [addExercise] directly off a plain shared MutableStateFlow
    // instead (mirrors the existing notification-deep-link mechanism). See
    // `MainActivity.pickedExerciseId`'s KDoc for the full story.

    // ------------------------------------------------------------------------ actions (P1)
    fun addExercise(exerciseId: String) = safeLaunch { repo.addExerciseToSession(sessionId, exerciseId) }

    /** §2.1 fix — the multi-select "Add (n)" call site used to fire one `safeLaunch` per exercise
     *  id, so N coroutines raced each other's read of `maxExerciseOrderIndex` before any of them
     *  had inserted. One coroutine, looping sequentially, guarantees each add sees the previous
     *  one's write (on top of `WorkoutRepository.addExerciseToSession`'s own transaction, which
     *  handles the case of two truly-concurrent callers). */
    fun addExercises(exerciseIds: List<String>) = safeLaunch {
        for (id in exerciseIds) repo.addExerciseToSession(sessionId, id)
    }
    fun removeExercise(blockId: String) = safeLaunch { repo.removeExerciseFromSession(blockId) }
    fun setExerciseNotes(blockId: String, notes: String) = safeLaunch { repo.setExerciseNotes(blockId, notes) }
    fun setExerciseRest(blockId: String, restSeconds: Int?) = safeLaunch { repo.setExerciseRest(blockId, restSeconds) }

    /** Feature addition — "I forgot to end this on time": lets the duration ring be corrected
     *  before Finish. Re-anchors the session's `startedAt`; the ticker keeps running afterwards.
     *  §1.5 fix — only updates the local ticker state on a true result, matching
     *  `WorkoutRepository.setSessionElapsedSeconds`'s new "false if the session is already gone"
     *  contract, instead of always assuming the write landed. */
    fun setElapsedSeconds(newElapsedSeconds: Long) = safeLaunch {
        // Item 8 — the success branch used to also write `_elapsedSeconds.value` directly. With
        // the derived ticker there is nothing to write: `setSessionElapsedSeconds` re-anchors the
        // session's `startedAt`, that re-emits through `state`, and the ticker restarts from the
        // new anchor. Strictly more correct — the local value and the DB can no longer disagree.
        if (!repo.setSessionElapsedSeconds(sessionId, newElapsedSeconds)) {
            _errorMessage.value = "This workout is no longer available."
            _errorToken.value++
        }
    }

    fun addSet(blockId: String, exerciseId: String) = safeLaunch {
        val previous = state.value.blocks.firstOrNull { it.block.id == blockId }?.previous ?: emptyMap()
        repo.addSet(blockId, sessionId, exerciseId, previous)
    }

    /** §2.9 — wires up the previously-dead `WorkoutRepository.reorderExercises`: the session
     *  screen's per-block up/down move buttons pass the whole block list, reordered. */
    fun reorderExercises(orderedBlockIds: List<String>) = safeLaunch { repo.reorderExercises(orderedBlockIds) }

    // §2.13 fix — column-scoped commits instead of a whole-row `updateSet(set.copy(...))` built
    // from a stale composition-time snapshot, so a set-cell commit can't clobber a concurrent
    // `toggleSetComplete`.
    fun updateSetWeight(id: String, weightKg: Float?) = safeLaunch { repo.setSetWeight(id, weightKg) }
    fun updateSetReps(id: String, reps: Int?) = safeLaunch { repo.setSetReps(id, reps) }
    fun updateSetDuration(id: String, durationSeconds: Int?) = safeLaunch { repo.setSetDuration(id, durationSeconds) }
    fun updateSetDistance(id: String, distanceMeters: Float?) = safeLaunch { repo.setSetDistance(id, distanceMeters) }
    // Item 4 — cancellation matrix: a deleted set can be the one whose completion started the
    // currently-running rest (there's no reliable way to attribute the single session-wide rest
    // timer to a specific set otherwise), so this cancels any active rest first — same blunt rule
    // `onToggleComplete`'s un-complete branch already applies. A no-op when no rest is running.
    fun deleteSet(id: String) = safeLaunch {
        cancelRest()
        repo.deleteSet(id)
    }
    fun toggleSetComplete(id: String) = safeLaunch { repo.toggleSetComplete(id) }

    private val _finished = MutableStateFlow(false)
    val finished = _finished.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    private val _errorToken = MutableStateFlow(0)
    val errorToken = _errorToken.asStateFlow()

    fun finish() = safeLaunch {
        // Item 4 — the session is ending; a pending rest timer no longer refers to anything.
        cancelRest()
        // Bug fix (BEAST_MODE_BUG_REPORT.md §1.5) — `finishSession` silently no-ops if the
        // session row is already gone (a race with a concurrent discard/sync eviction); only
        // navigate away when it actually saved, instead of always reporting success.
        if (repo.finishSession(sessionId)) {
            _finished.value = true
        } else {
            _errorMessage.value = "This workout is no longer available."
            _errorToken.value++
        }
    }

    fun discard() = safeLaunch {
        // Item 4 — same reasoning as `finish()`.
        cancelRest()
        repo.discardSession(sessionId)
        _finished.value = true
    }

    private companion object {
        /** M7 — how long the "Rest over" pill stays up after the countdown hits zero, before it's
         *  cleared so it doesn't stick around for the rest of the session. */
        const val REST_OVER_HOLD_MILLIS = 5_000L
        const val KEY_PENDING_CAPTURE_PATH = "pending_capture_file_path"
    }
}
