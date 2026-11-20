package com.thriple.define

import android.Manifest
import android.os.Build
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.thriple.define.data.Store
import com.thriple.define.engine.AppState
import com.thriple.define.engine.Area
import com.thriple.define.engine.DayType
import com.thriple.define.engine.Effort
import com.thriple.define.engine.Logic
import com.thriple.define.engine.PodResult
import com.thriple.define.engine.Ranks
import com.thriple.define.engine.Rewards
import com.thriple.define.engine.SessionPlan
import com.thriple.define.engine.SurgeResult
import com.thriple.define.engine.Windows
import com.thriple.define.reminders.Reminders
import com.thriple.define.ui.CheckInScreen
import com.thriple.define.ui.EffortScreen
import com.thriple.define.ui.FxLayer
import com.thriple.define.ui.FxState
import com.thriple.define.ui.Haptics
import com.thriple.define.ui.HomeScreen
import com.thriple.define.ui.LivingBackground
import com.thriple.define.ui.LocalFx
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
        setContent { DefineApp(this) }
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

    fun save(s: AppState) { state = s; Store.save(activity, s) }
    fun minuteNow(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

    BackHandler(enabled = screen != Screen.Home) {
        when (screen) {
            is Screen.Workout -> confirmQuit = true
            is Screen.Pod, is Screen.Transform, is Screen.Rate -> Unit // finish the reward flow
            else -> screen = Screen.Home
        }
    }

    val time by rememberFrameTime()
    CompositionLocalProvider(LocalFx provides fx) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            val o = fx.shakeOffset(time)
            translationX = o.x; translationY = o.y
        }) {
            LivingBackground(Palette.forHour(LocalTime.now().hour))
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
                    )
                    Screen.CheckIn -> CheckInScreen { soreness: Map<Area, Int> ->
                        val (ns, plan) = Logic.checkIn(state, Store.today(), soreness)
                        save(ns)
                        screen = Screen.Workout(plan)
                    }
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
                        onSound = { save(state.copy(soundOn = it)); Sfx.enabled = it },
                        onReminders = { save(state.copy(remindersOn = it)); Reminders.scheduleNext(activity) },
                        onAskNotify = { activity.requestNotifications() },
                        onTestReminder = { Reminders.show(activity, "Test: mission is live", "This is what your reminders look like.") },
                        onBack = { screen = Screen.Home },
                    )
                }
            }
            FxLayer(fx)
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
    val outcome = Logic.completeSession(state, Store.today(), plan, completed, effort, reps, inWindow)
    val (afterPod, pod) = Rewards.openPod(outcome.state, rng, bonusXp = if (plan.welcomeBack) 100 else 0)
    val rankAfter = Ranks.index(afterPod.xp)
    Store.save(activity, afterPod)
    Reminders.scheduleNext(activity)
    Reminders.clear(activity)
    val rankUp = if (rankAfter > rankBefore) Ranks.names[rankAfter] else null
    return commit(afterPod, Screen.Pod(pod, outcome.xpGained, rankUp, outcome.shieldEarned))
}
