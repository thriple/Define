package com.thriple.define.data

import android.content.Context
import com.thriple.define.engine.AppState
import com.thriple.define.engine.DayType
import com.thriple.define.engine.Effort
import com.thriple.define.engine.SessionRecord
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Saves AppState as one JSON blob in SharedPreferences. */
object Store {
    private const val PREFS = "define"
    private const val KEY = "state_v1"

    fun today(): Long = LocalDate.now().toEpochDay()

    fun load(context: Context): AppState {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return AppState(startDay = today())
        return try { fromJson(JSONObject(raw)) } catch (e: Exception) { AppState(startDay = today()) }
    }

    fun save(context: Context, state: AppState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, toJson(state).toString()).apply()
    }

    private fun mapToJson(m: Map<String, Int>) = JSONObject().apply { m.forEach { (k, v) -> put(k, v) } }
    private fun jsonToMap(o: JSONObject?): Map<String, Int> {
        if (o == null) return emptyMap()
        val out = mutableMapOf<String, Int>()
        val keys = o.keys()
        while (keys.hasNext()) { val k = keys.next(); out[k] = o.optInt(k) }
        return out
    }
    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }
    private fun ints(a: JSONArray?): List<Int> = if (a == null) emptyList() else (0 until a.length()).map { a.getInt(it) }

    fun toJson(s: AppState): JSONObject = JSONObject().apply {
        put("startDay", s.startDay)
        put("rotationIndex", s.rotationIndex)
        put("steps", mapToJson(s.steps))
        put("pendingUp", JSONArray(s.pendingUp.toList()))
        put("lastSessionDay", s.lastSessionDay)
        put("streak", s.streak)
        put("bestStreak", s.bestStreak)
        put("streakCountedDay", s.streakCountedDay)
        put("shields", s.shields)
        put("xp", s.xp)
        put("power", s.power)
        put("sinceElite", s.sinceElite)
        put("sinceLegendary", s.sinceLegendary)
        put("gear", mapToJson(s.gear))
        put("scrap", s.scrap)
        put("shownStage", s.shownStage)
        put("weights", JSONArray(s.weights.toList()))
        put("soundOn", s.soundOn)
        put("remindersOn", s.remindersOn)
        put("history", JSONArray().apply {
            s.history.forEach { r ->
                put(JSONObject().apply {
                    put("day", r.day); put("type", r.type.name); put("sets", r.sets); put("reps", r.reps)
                    put("effort", r.effort?.name ?: ""); put("inWindow", r.inWindow)
                })
            }
        })
    }

    fun fromJson(o: JSONObject): AppState {
        val hist = o.optJSONArray("history")
        val history = if (hist == null) emptyList() else (0 until hist.length()).map { i ->
            val r = hist.getJSONObject(i)
            SessionRecord(
                day = r.getLong("day"),
                type = DayType.valueOf(r.getString("type")),
                sets = r.optInt("sets"),
                reps = r.optInt("reps"),
                effort = r.optString("effort").takeIf { it.isNotEmpty() }?.let { Effort.valueOf(it) },
                inWindow = r.optBoolean("inWindow"),
            )
        }
        return AppState(
            startDay = o.getLong("startDay"),
            rotationIndex = o.optInt("rotationIndex"),
            steps = jsonToMap(o.optJSONObject("steps")),
            pendingUp = strings(o.optJSONArray("pendingUp")).toSet(),
            lastSessionDay = o.optLong("lastSessionDay", -1),
            streak = o.optInt("streak"),
            bestStreak = o.optInt("bestStreak"),
            streakCountedDay = o.optLong("streakCountedDay", -1),
            shields = o.optInt("shields"),
            xp = o.optInt("xp"),
            power = o.optInt("power"),
            sinceElite = o.optInt("sinceElite"),
            sinceLegendary = o.optInt("sinceLegendary"),
            gear = jsonToMap(o.optJSONObject("gear")),
            scrap = o.optInt("scrap"),
            shownStage = o.optInt("shownStage", 1),
            history = history,
            weights = ints(o.optJSONArray("weights")).toSet().ifEmpty { setOf(30) },
            soundOn = o.optBoolean("soundOn", true),
            remindersOn = o.optBoolean("remindersOn", true),
        )
    }
}
