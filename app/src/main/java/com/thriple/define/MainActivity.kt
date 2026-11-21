package com.thriple.define

import android.Manifest
import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.thriple.define.data.Store
import com.thriple.define.engine.AppState
import com.thriple.define.engine.Body
import com.thriple.define.engine.DayType
import com.thriple.define.engine.Effort
import com.thriple.define.engine.Logic
import com.thriple.define.engine.PodResult
import com.thriple.define.engine.Ranks
import com.thriple.define.engine.Rewards
import com.thriple.define.engine.SessionPlan
import com.thriple.define.engine.SurgeResult
import com.thriple.define.engine.Windows
import com.thriple.define.engine.music.Scene
import com.thriple.define.reminders.Reminders
import com.thriple.define.ui.AdjustScreen
import com.thriple.define.ui.BodyCheckScreen
import com.thriple.define.ui.RecoveryScreen
import com.thriple.define.ui.ReplyBanner
import com.thriple.define.ui.EffortScreen
import com.thriple.define.ui.FxLayer
import com.thriple.define.ui.FxState
import com.thriple.define.ui.Haptics
import com.thriple.define.ui.HomeScreen
import com.thriple.define.ui.LivingBackground
import com.thriple.define.ui.LocalFx
import com.thriple.define.ui.LocalTilt
import com.thriple.define.ui.rememberTilt
import com.thriple.define.ui.Music
import com.thriple.define.ui.Palette
import com.thriple.define.ui.PodScreen
import com.thriple.define.ui.SettingsScreen
import com.thriple.define.ui.Sfx
import com.thriple.define.ui.TransformScreen
import com.thriple.define.ui.WorkoutScreen
import com.thriple.define.ui.rememberFrameTime
import java.time.LocalTime
import kotlin.random.Random

sealed interface Screen {
    data object Home : Screen
    data object CheckIn : Screen
    data object Body : Screen
    data class Adjust(val result: Logic.CheckInResult) : Screen
    data object Recovery : Screen
    data class Workout(val plan: SessionPlan) : Screen
    data class Rate(val plan: SessionPlan, val completed: Map<String, Boolean>, val reps: Int) : Screen
    data class Pod(val pod: PodResult, val xp: Int, val rankUp: String?, val shield: Boolean) : Screen
    data class Transform(val from: Int, val to: Int) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    private var canNotify by mutableStateOf(false)
    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted
        Reminders.scheduleNext(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Sfx.init(this)
        Haptics.init(this)
        Reminders.ensureChannel(this)
        canNotify = Reminders.canNotify(this)
        if (!canNotify && Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        Reminders.scheduleNext(this)
        openBody = intent?.getBooleanExtra(Reminders.EXTRA_OPEN_BODY, false) == true
        setContent { DefineApp(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(Reminders.EXTRA_OPEN_BODY, false)) openBody = true
    }

    /** Set when the evening check-in nudge was tapped. */
    var openBody by mutableStateOf(false)

    override fun onStart() {
        super.onStart()
        if (Store.load(this).soundOn) Music.start(this)
    }

    override fun onStop() {
        super.onStop()
        Music.stop()
    }

    override fun onResume() {
        super.onResume()
        canNotify = Reminders.canNotify(this)
    }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val notificationsAllowed: Boolean get() = canNotify
}

@Composable
private fun DefineApp(activity: MainActivity) {
    val fx = remember { FxState() }
    var state by remember { mutableStateOf(Logic.refreshStreak(Store.load(activity), Store.today())) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var confirmQuit by remember { mutableStateOf(false) }
    val rng = remember { Random(System.nanoTime()) }
    Sfx.enabled = state.soundOn
    Music.musicWanted = state.musicOn

    // Music for every screen outside the workout (the workout screen drives its own).
    LaunchedEffect(screen::class) {
        when (screen) {
            Screen.Home, Screen.Settings -> Music.scene(Scene.HOME, 100f, homeEnergy(state))
            Screen.CheckIn, Screen.Body, Screen.Recovery -> Music.scene(Scene.HOME, 100f, (homeEnergy(state) + 0.1f).coerceAtMost(1f))
            is Screen.Adjust -> Music.scene(Scene.CALM, 100f)
            is Screen.Rate -> Music.scene(Scene.CALM, 100f)
            is Screen.Pod -> Music.scene(Scene.POD, 100f)
            else -> Unit
        }
    }

    fun save(s: AppState) { state = s; Store.save(activity, s) }
    fun minuteNow(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

    BackHandler(enabled = screen != Screen.Home) {
        when (screen) {
            is Screen.Workout -> confirmQuit = true
            is Screen.Pod, is Screen.Transform, is Screen.Rate -> Unit // finish the reward flow
            else -> screen = Screen.Home
        }
    }

    var reply by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activity.openBody) {
        if (activity.openBody) { activity.openBody = false; if (screen !is Screen.Workout) screen = Screen.Body }
    }

    val time by rememberFrameTime()
    val tilt = rememberTilt()
    CompositionLocalProvider(LocalFx provides fx, LocalTilt provides tilt) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            val o = fx.shakeOffset(time)
            translationX = o.x; translationY = o.y
        }) {
            LivingBackground(Palette.forHour(LocalTime.now().hour), meteorsPerMinute = 3f + state.streak * 0.4f)
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = 0.7f, stiffness = 260f), initialScale = 0.85f) + fadeIn(tween(220))) togetherWith
                        (scaleOut(tween(220), targetScale = 1.12f) + fadeOut(tween(200)))
                },
                contentKey = { it::class },
                label = "screen",
            ) { s ->
                when (s) {
                    Screen.Home -> HomeScreen(
                        state, Store.today(),
                        onStart = { screen = Screen.CheckIn },
                        onSettings = { screen = Screen.Settings },
                        onBody = { screen = Screen.Body },
                    )
                    Screen.CheckIn -> BodyCheckScreen(state.body.lastOrNull(), preWorkout = true, onBack = { screen = Screen.Home }) { check ->
                        val r = Logic.checkIn(state, Store.today(), check)
                        save(r.state)
                        screen = if (r.anyway != null) Screen.Adjust(r) else Screen.Workout(r.plan)
                    }
                    Screen.Body -> BodyCheckScreen(state.body.lastOrNull(), preWorkout = false, onBack = { screen = Screen.Home }) { check ->
                        val before = state
                        save(Logic.logBody(state, check))
                        reply = Body.reply(check, before, System.currentTimeMillis())
                        Music.tap(); Haptics.click()
                        fx.confetti(40)
                        Reminders.scheduleNext(activity)
                        screen = Screen.Home
                    }
                    is Screen.Adjust -> AdjustScreen(
                        title = "Today: " + s.result.plan.type.title,
                        notes = s.result.notes,
                        onGo = { screen = Screen.Workout(s.result.plan) },
                        onAnyway = { screen = Screen.Workout(s.result.anyway ?: s.result.plan) },
                    )
                    Screen.Recovery -> RecoveryScreen(state, Store.today()) { screen = Screen.Settings }
                    is Screen.Workout -> WorkoutScreen(
                        plan = s.plan,
                        primerSeen = state.seenPrimer,
                        onPrimerSeen = { save(state.copy(seenPrimer = true)) },
                        onSurge = {
                            val (ns, surge) = Logic.rollSurge(state, rng)
                            save(ns)
                            surge
                        },
                        onFinish = { completed, reps ->
                            screen = if (s.plan.type == DayType.RECHARGE) {
                                finishSession(activity, state, s.plan, completed, null, reps, minuteNow(), rng) { ns, sc -> save(ns); sc }
                            } else {
                                Screen.Rate(s.plan, completed, reps)
                            }
                        },
                    )
                    is Screen.Rate -> EffortScreen { effort ->
                        screen = finishSession(activity, state, s.plan, s.completed, effort, s.reps, minuteNow(), rng) { ns, sc -> save(ns); sc }
                    }
                    is Screen.Pod -> PodScreen(s.pod, s.xp, s.rankUp, s.shield, state.streak) {
                        val earned = Logic.earnedStage(state)
                        screen = if (earned > state.shownStage) Screen.Transform(state.shownStage, earned) else Screen.Home
                    }
                    is Screen.Transform -> TransformScreen(s.from, s.to) {
                        save(state.copy(shownStage = s.to))
                        screen = Screen.Home
                    }
                    Screen.Settings -> SettingsScreen(
                        state = state,
                        canNotify = activity.notificationsAllowed,
                        onWeights = { save(state.copy(weights = it)) },
                        onSound = {
                            save(state.copy(soundOn = it)); Sfx.enabled = it
                            if (it) Music.start(activity) else Music.stop()
                        },
                        onMusic = { save(state.copy(musicOn = it)); Music.musicWanted = it },
                        onReminders = { save(state.copy(remindersOn = it)); Reminders.scheduleNext(activity) },
                        onAskNotify = { activity.requestNotifications() },
                        onTestReminder = { Reminders.show(activity, "Test: mission is live", "This is what your reminders look like.") },
                        onRecovery = { screen = Screen.Recovery },
                        onBack = { screen = Screen.Home },
                    )
                }
            }
            FxLayer(fx)
            ReplyBanner(reply) { reply = null }
        }
        if (confirmQuit) {
            AlertDialog(
                onDismissRequest = { confirmQuit = false },
                title = { Text("Abandon mission?") },
                text = { Text("Progress in this session won't count.") },
                confirmButton = { TextButton({ confirmQuit = false; screen = Screen.Home }) { Text("Abandon") } },
                dismissButton = { TextButton({ confirmQuit = false }) { Text("Keep going") } },
            )
        }
    }
}

/** Applies the finished session, opens a supply pod, re-plans reminders, and returns the pod screen. */
private fun finishSession(
    activity: MainActivity,
    state: AppState,
    plan: SessionPlan,
    completed: Map<String, Boolean>,
    effort: Effort?,
    reps: Int,
    minute: Int,
    rng: Random,
    commit: (AppState, Screen) -> Screen,
): Screen {
    val inWindow = Windows.current(minute) != null
    val rankBefore = Ranks.index(state.xp)
    val outcome = Logic.completeSession(state, Store.today(), plan, completed, effort, reps, inWindow, now = System.currentTimeMillis())
    val (afterPod, pod) = Rewards.openPod(outcome.state, rng, bonusXp = if (plan.welcomeBack) 100 else 0)
    val rankAfter = Ranks.index(afterPod.xp)
    Store.save(activity, afterPod)
    Reminders.scheduleNext(activity)
    Reminders.clear(activity)
    val rankUp = if (rankAfter > rankBefore) Ranks.names[rankAfter] else null
    return commit(afterPod, Screen.Pod(pod, outcome.xpGained, rankUp, outcome.shieldEarned))
}

/** How lively the home-screen music is: grows with overall progress and the current streak. */
private fun homeEnergy(s: AppState): Float =
    (0.2f + 0.55f * Logic.progress(s).toFloat() + 0.25f * (s.streak / 21f).coerceAtMost(1f)).coerceIn(0f, 1f)
