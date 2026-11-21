package com.thriple.define.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import com.thriple.define.engine.Pace
import com.thriple.define.engine.music.MusicEngine
import com.thriple.define.engine.music.Scene
import java.time.LocalDate

/**
 * Streams the synth engine to an AudioTrack on its own audio-priority thread, and exposes the beat clock
 * of what is actually coming out of the speaker so the screen can follow the music.
 *
 * It never requests audio focus. If something else (a podcast, your own playlist) is already playing when
 * the app opens, the music stays quiet for that visit and only the cues play.
 */
object Music {
    private var engine: MusicEngine? = null
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private var trackStartFrame = 0L
    private var lastHead = -1
    private var lastHeadNanos = 0L
    private var lastScene: Triple<Scene, Float?, Float?>? = null

    /** User setting. */
    var musicWanted = true
        set(v) { field = v; engine?.music(v && !otherAudio) }

    /** Something else was playing when we started. */
    var otherAudio = false
        private set

    val isRunning: Boolean get() = running

    fun start(context: Context) {
        if (running) return
        try {
            val am = context.getSystemService(AudioManager::class.java)
            otherAudio = am?.isMusicActive == true
            val rate = am?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
            val e = engine?.takeIf { it.sampleRate == rate } ?: MusicEngine(rate, LocalDate.now().toEpochDay())
            engine = e
            e.music(musicWanted && !otherAudio)
            lastScene?.let { (s, t, en) -> e.scene(s, t, en) }
            val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(maxOf(min, rate / 10 * 8))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track = t
            trackStartFrame = e.framesRendered
            lastHead = -1
            running = true
            t.play()
            thread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                val frames = 256
                val buf = FloatArray(frames * 2)
                while (running) {
                    e.render(buf, frames)
                    val w = t.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
                    if (w < 0) break
                }
            }, "define-music").apply { start() }
        } catch (_: Exception) {
            running = false
            track?.release(); track = null
        }
    }

    fun stop() {
        if (!running) return
        running = false
        try { thread?.join(500) } catch (_: InterruptedException) { }
        thread = null
        try { track?.pause(); track?.flush(); track?.release() } catch (_: Exception) { }
        track = null
    }

    /** The beat being heard right now, or null when no audio is running. */
    fun beatNow(): Double? {
        val e = engine ?: return null
        val t = track ?: return null
        if (!running) return null
        val head = try { t.playbackHeadPosition } catch (_: Exception) { return null }
        val now = System.nanoTime()
        if (head != lastHead) { lastHead = head; lastHeadNanos = now }
        // the head moves in bursts; extrapolate between updates (at most 60 ms)
        val extra = ((now - lastHeadNanos) / 1e9 * e.sampleRate).toLong().coerceAtMost(e.sampleRate * 60L / 1000)
        return e.beatAt(trackStartFrame + head + extra)
    }

    fun scene(s: Scene, tempo: Float? = null, energy: Float? = null) {
        lastScene = Triple(s, tempo ?: lastScene?.second, energy ?: lastScene?.third)
        engine?.scene(s, tempo, energy)
    }

    // Cues return true when the music engine played them (so the caller can skip its fallback sound).
    private inline fun cue(block: (MusicEngine) -> Unit): Boolean {
        val e = engine
        if (!running || e == null) return false
        block(e); return true
    }

    fun pace(p: Pace, startBeat: Double, reps: Int) = cue { it.pace(p, startBeat, reps) }
    fun stopPace() = cue { it.stopPace() }
    fun roundDone(rarity: Int) = cue { it.roundDone(rarity) }
    fun crack(n: Int) = cue { it.crack(n) }
    fun reveal(rarity: Int) = cue { it.reveal(rarity) }
    fun rankUp() = cue { it.rankUp() }
    fun charge(seconds: Float) = cue { it.charge(seconds) }
    fun boom() = cue { it.boom() }
    fun fail() = cue { it.fail() }
    fun riser(seconds: Float) = cue { it.riser(seconds) }
}
