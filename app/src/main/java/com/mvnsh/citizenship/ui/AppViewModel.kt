package com.mvnsh.citizenship.ui

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mvnsh.citizenship.CitizenshipApp
import com.mvnsh.citizenship.data.BankRepository
import com.mvnsh.citizenship.data.model.NotifPrefs
import com.mvnsh.citizenship.data.model.ProgressState
import com.mvnsh.citizenship.data.model.SessionState
import com.mvnsh.citizenship.domain.DateUtils
import com.mvnsh.citizenship.domain.Stats
import com.mvnsh.citizenship.domain.StudyEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The single activity-scoped state holder.
 *
 * The design prototype kept route and tab in state; here the nav graph owns navigation,
 * so this holds only what the prototype also held: the loaded bank, persisted progress
 * and the live session.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx = app as CitizenshipApp
    private val progressRepo = appCtx.progressRepository

    private val _bank = MutableStateFlow<BankRepository.Bank?>(null)

    /** Null until the assets are parsed. */
    val bank: StateFlow<BankRepository.Bank?> = _bank.asStateFlow()

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    val progress: StateFlow<ProgressState> = progressRepo.state

    /** Derived view of the persisted session so screens can observe it directly. */
    val session: StateFlow<SessionState?> = progress
        .map { it.session }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _mockSecondsLeft = MutableStateFlow(0)
    val mockSecondsLeft: StateFlow<Int> = _mockSecondsLeft.asStateFlow()

    private val _snacks = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snacks: SharedFlow<String> = _snacks.asSharedFlow()

    init {
        retryLoad()
        viewModelScope.launch { tickMockTimer() }
    }

    fun retryLoad() {
        _loadFailed.value = false
        viewModelScope.launch {
            runCatching { appCtx.bankRepository.load() }
                .onSuccess { _bank.value = it }
                .onFailure { _loadFailed.value = true }
        }
    }

    fun toast(message: String) {
        _snacks.tryEmit(message)
    }

    // ---- sessions -------------------------------------------------------

    fun startSession(mode: StudyEngine.Mode, topicKey: String? = null) {
        val loaded = _bank.value ?: return
        val ids = StudyEngine.buildSession(mode, loaded.questions, progress.value, topicKey)
        if (ids.isEmpty()) {
            toast("Nothing to practise here yet")
            return
        }
        progressRepo.mutate {
            it.copy(
                session = SessionState(
                    mode = mode.name,
                    topicKey = topicKey,
                    label = StudyEngine.label(mode, topicKey),
                    ids = ids,
                    startedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun startMock() {
        val loaded = _bank.value ?: return
        val ids = StudyEngine.buildSession(StudyEngine.Mode.MOCK, loaded.questions, progress.value)
        if (ids.isEmpty()) {
            toast("Nothing to practise here yet")
            return
        }
        val now = System.currentTimeMillis()
        progressRepo.mutate {
            it.copy(
                session = SessionState(
                    mode = StudyEngine.Mode.MOCK.name,
                    label = "Mock test",
                    ids = ids,
                    startedAtEpochMs = now,
                    timed = true,
                    deadlineEpochMs = now + StudyEngine.MOCK_SECONDS * 1000L,
                ),
            )
        }
    }

    /** Home's primary button: resume an unfinished session, otherwise start one. */
    fun resumeOrStart() {
        val current = progress.value.session
        if (current == null || current.submitted) startSession(StudyEngine.Mode.CONTINUE)
    }

    /** Starts a session over exactly the ids missed in the session just finished. */
    fun practiceMissed(ids: List<Int>) {
        if (ids.isEmpty()) return
        progressRepo.mutate {
            it.copy(
                session = SessionState(
                    mode = StudyEngine.Mode.WEAK.name,
                    label = "Missed questions",
                    ids = ids,
                    startedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun pick(optionIndex: Int) {
        val loaded = _bank.value ?: return
        val s = progress.value.session ?: return
        val questionId = s.ids.getOrNull(s.index) ?: return
        val q = loaded.byId[questionId] ?: return

        if (s.mode == StudyEngine.Mode.MOCK.name) {
            // No feedback during a mock; the answer stays changeable until submit.
            progressRepo.mutate { it.copy(session = s.copy(marks = s.marks + (q.id to optionIndex))) }
            return
        }
        if (s.picked != null) return // already revealed, so the question is locked

        val correct = optionIndex == q.answer
        val milestonesBefore = Stats.achievements(loaded.questions, progress.value).size
        progressRepo.mutate { p ->
            Stats.registerAnswer(p, q.id, correct, DateUtils.today(), DateUtils.shiftDay(-1)).copy(
                session = s.copy(
                    picked = optionIndex,
                    right = s.right + if (correct) 1 else 0,
                    marks = s.marks + (q.id to optionIndex),
                ),
            )
        }
        announceProgress(milestonesBefore)
    }

    private fun announceProgress(milestonesBefore: Int) {
        val loaded = _bank.value ?: return
        val p = progress.value
        val unlocked = Stats.achievements(loaded.questions, p)
        if (unlocked.size > milestonesBefore) {
            val label = Stats.MILESTONES.last { it.key in unlocked }.label
            toast("Milestone reached — $label")
        } else if (p.goalDone == p.goalTarget) {
            toast("Daily goal met — streak is safe")
        }
    }

    fun next() {
        val s = progress.value.session ?: return
        if (s.index + 1 >= s.ids.size) {
            progressRepo.mutate { it.copy(session = s.copy(submitted = true, finalRight = s.right)) }
            viewModelScope.launch { progressRepo.flush() }
            return
        }
        progressRepo.mutate { it.copy(session = s.copy(index = s.index + 1, picked = null)) }
    }

    fun goTo(index: Int) {
        val s = progress.value.session ?: return
        if (s.ids.isEmpty()) return
        progressRepo.mutate { it.copy(session = s.copy(index = index.coerceIn(s.ids.indices), picked = null)) }
    }

    fun submitMock(auto: Boolean = false) {
        val loaded = _bank.value ?: return
        val s = progress.value.session ?: return
        if (s.submitted) return

        progressRepo.mutate { p ->
            val (next, right) = Stats.registerMock(
                p, s.ids, s.marks, loaded.byId, DateUtils.today(), DateUtils.shiftDay(-1),
            )
            val pct = if (s.ids.isEmpty()) 0 else (right * 100.0 / s.ids.size).roundToInt()
            next.copy(session = s.copy(submitted = true, finalRight = right, finalPct = pct))
        }
        viewModelScope.launch { progressRepo.flush() }
        if (auto) toast("Time up — test submitted")
    }

    /** Keeps the session so home can resume it. */
    fun keepSessionAndExit() {
        viewModelScope.launch { progressRepo.flush() }
    }

    fun discardSession() {
        progressRepo.mutate { it.copy(session = null) }
    }

    /**
     * Drives the mock countdown off an absolute deadline rather than a decrementing
     * counter, so backgrounding the app does not pause it.
     */
    private suspend fun tickMockTimer() {
        while (true) {
            val s = progress.value.session
            val deadline = s?.deadlineEpochMs
            if (s != null && s.timed && !s.submitted && deadline != null) {
                val left = ((deadline - System.currentTimeMillis()) / 1000L).toInt()
                _mockSecondsLeft.value = left.coerceAtLeast(0)
                if (left <= 0) submitMock(auto = true)
            } else {
                _mockSecondsLeft.value = 0
            }
            delay(TIMER_TICK_MS)
        }
    }

    // ---- bookmarks and weak list ---------------------------------------

    fun toggleBookmark(id: Int) {
        var added = false
        progressRepo.mutate { p ->
            if (id in p.bookmarks) {
                p.copy(bookmarks = p.bookmarks - id)
            } else {
                added = true
                p.copy(bookmarks = p.bookmarks + id)
            }
        }
        toast(if (added) "Saved to bookmarks" else "Removed from bookmarks")
    }

    /** Clears the miss count so the question leaves the weak list, keeping its history. */
    fun clearWeak(id: Int) {
        progressRepo.mutate { p ->
            val rec = p.seen[id] ?: return@mutate p
            p.copy(seen = p.seen + (id to rec.copy(m = 0)))
        }
        toast("Cleared from weak list")
    }

    // ---- settings and onboarding ---------------------------------------

    fun finishOnboarding() = progressRepo.mutate { it.copy(onboarded = true) }

    fun setGoalTarget(n: Int) = progressRepo.mutate { it.copy(goalTarget = n) }

    fun setTestDate(iso: String?) = progressRepo.mutate { it.copy(testDate = iso) }

    fun setTheme(name: String) = progressRepo.mutate { it.copy(theme = name) }

    fun setNotif(daily: Boolean? = null, streak: Boolean? = null, test: Boolean? = null) =
        progressRepo.mutate { p ->
            p.copy(
                notif = NotifPrefs(
                    daily = daily ?: p.notif.daily,
                    streak = streak ?: p.notif.streak,
                    test = test ?: p.notif.test,
                ),
            )
        }

    fun resetProgress() {
        // The design's reset dialog is explicit: "Bookmarks are kept."
        val keptBookmarks = progress.value.bookmarks
        val keptTheme = progress.value.theme
        viewModelScope.launch {
            progressRepo.replace(
                ProgressState(onboarded = true, bookmarks = keptBookmarks, theme = keptTheme),
            )
            toast("Progress reset")
        }
    }

    fun clearBookmarks() {
        progressRepo.mutate { it.copy(bookmarks = emptyList()) }
        toast("Bookmarks cleared")
    }

    // ---- debug helpers --------------------------------------------------

    /** Reproduces the design canvas's demo dataset. Debug builds only. */
    fun seedDemo() {
        val loaded = _bank.value ?: return
        viewModelScope.launch {
            val seen = loaded.questions.take(340).mapIndexed { i, q ->
                q.id to com.mvnsh.citizenship.data.model.SeenStat(
                    s = 1 + if (i % 3 == 0) 1 else 0,
                    m = if (i % 23 == 0) 2 else if (i % 6 == 0) 1 else 0,
                )
            }.toMap()
            progressRepo.replace(
                ProgressState(
                    onboarded = true, answered = 340, correct = 279, seen = seen,
                    mocks = listOf(
                        com.mvnsh.citizenship.data.model.MockAttempt(70, DateUtils.shiftDay(-9)),
                        com.mvnsh.citizenship.data.model.MockAttempt(80, DateUtils.shiftDay(-4)),
                        com.mvnsh.citizenship.data.model.MockAttempt(85, DateUtils.shiftDay(-1)),
                    ),
                    bookmarks = listOf(2, 7, 10, 44, 120),
                    goalTarget = 20, goalDone = 14, goalDate = DateUtils.today(),
                    streak = 12, best = 14, lastDay = DateUtils.shiftDay(-1),
                    testDate = DateUtils.shiftDay(38),
                    week = listOf(12, 16, 20, 18, 22, 14, 14), weekDate = DateUtils.today(),
                ),
            )
            toast("Demo data loaded")
        }
    }

    fun freshInstall() {
        viewModelScope.launch {
            progressRepo.replace(ProgressState())
            toast("Reset to fresh install")
        }
    }

    /**
     * Forces the error state so the shell's failure branch is reachable from a test.
     * A fake BankRepository would be tidier, but the app has no DI container and adding
     * one for a single test is not worth it.
     */
    @VisibleForTesting
    fun forceLoadFailureForTest() {
        _bank.value = null
        _loadFailed.value = true
    }

    companion object {
        private const val TIMER_TICK_MS = 500L
    }
}

/** Every screen shares the activity-scoped instance. */
fun Fragment.appViewModel(): AppViewModel {
    val vm: AppViewModel by activityViewModels()
    return vm
}
