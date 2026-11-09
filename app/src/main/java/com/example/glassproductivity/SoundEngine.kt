package com.example.glassproductivity

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.Random
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Synthesizes every sound procedurally (no audio files):
 * high-resolution (48 kHz, 32-bit float, stereo) ambient scenes with physically-inspired models
 * (rain drops, water bubbles, fire crackle, wind gusts, crowd murmur...) plus a soft room reverb,
 * a calm wooden tick-tock, and a completion chime.
 */
class SoundEngine {
    private val sr = 48000

    @Volatile private var ambientRunning = false
    @Volatile private var tickRunning = false
    @Volatile private var ambientVolume = 0.7f
    @Volatile private var tickVolume = 0.7f
    private var ambientTrack: AudioTrack? = null
    private var tickTrack: AudioTrack? = null
    private val lock = Any()

    private fun newTrack(bufferBytes: Int, mode: Int = AudioTrack.MODE_STREAM, stereo: Boolean = false, float: Boolean = false): AudioTrack =
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(if (float) AudioFormat.ENCODING_PCM_FLOAT else AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sr)
                    .setChannelMask(if (stereo) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(mode)
            .build()

    // ---------------------------------------------------------------- ambient

    fun startAmbient(kind: Int, volume: Float) {
        stopAmbient()
        if (kind <= 0) return
        ambientVolume = volume
        val frames = 2048
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = try {
            newTrack(maxOf(minBuf, frames * 2 * 4 * 3), AudioTrack.MODE_STREAM, stereo = true, float = true)
        } catch (_: Exception) { return }
        synchronized(lock) { ambientTrack = track }
        ambientRunning = true
        try { track.play() } catch (_: Exception) { ambientRunning = false; return }
        thread(name = "waqt-ambient") {
            val left = AmbientGenerator(kind, sr, 0L, System.nanoTime(), 0)
            val right = AmbientGenerator(kind, sr, sr * 5L + 777L, System.nanoTime() + 99991L, 1)
            val buf = FloatArray(frames * 2)
            var fade = 0f
            while (ambientRunning && ambientTrack === track) {
                val v = ambientVolume
                for (i in 0 until frames) {
                    if (fade < 1f) fade = (fade + 1f / (sr * 1.2f)).coerceAtMost(1f)
                    buf[i * 2] = softClip(left.next() * v * fade)
                    buf[i * 2 + 1] = softClip(right.next() * v * fade)
                }
                try {
                    if (track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING) < 0) break
                } catch (_: Exception) { break }
            }
        }
    }

    private fun softClip(x: Float): Float = x / (1f + abs(x) * 0.6f) * 1.3f

    fun setAmbientVolume(v: Float) {
        ambientVolume = v
        tickVolume = v
    }

    fun stopAmbient() {
        ambientRunning = false
        val t: AudioTrack?
        synchronized(lock) { t = ambientTrack; ambientTrack = null }
        killTrack(t)
    }

    // ---------------------------------------------------------------- tick-tock

    fun startTick(volume: Float) {
        tickVolume = volume
        if (tickRunning) return
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val second = buildTickLoop()
        val track = try { newTrack(maxOf(minBuf, second.size * 2)) } catch (_: Exception) { return }
        synchronized(lock) { tickTrack = track }
        tickRunning = true
        try { track.play() } catch (_: Exception) { tickRunning = false; return }
        thread(name = "waqt-tick") {
            val out = ShortArray(second.size)
            while (tickRunning && tickTrack === track) {
                val v = tickVolume.coerceIn(0.1f, 1f)
                for (i in out.indices) out[i] = (second[i] * v).toInt().toShort()
                try {
                    if (track.write(out, 0, out.size) < 0) break
                } catch (_: Exception) { break }
            }
        }
    }

    fun stopTick() {
        tickRunning = false
        val t: AudioTrack?
        synchronized(lock) { t = tickTrack; tickTrack = null }
        killTrack(t)
    }

    /** Two calm seconds: a soft "tick" at 0.0s and a lower "tock" at 1.0s (60 BPM, wooden and gentle). */
    private fun buildTickLoop(): ShortArray {
        val out = FloatArray(sr * 2)
        fun click(at: Int, freq: Double, amp: Float) {
            val len = (sr * 0.18).toInt()
            val r = Random(at.toLong() + 7L)
            var lp = 0f
            for (i in 0 until len) {
                if (at + i >= out.size) break
                val t = i.toDouble() / sr
                val env = exp(-t * 38.0)
                val body = sin(2 * PI * freq * t) * env + 0.35 * sin(2 * PI * freq * 2.4 * t) * exp(-t * 70.0)
                lp += 0.12f * ((r.nextFloat() * 2f - 1f) - lp)
                val knock = lp * exp(-t * 220.0).toFloat()
                val fadeIn = if (i < 80) i / 80f else 1f
                out[at + i] += ((body * 0.75 + knock * 0.6) * amp * fadeIn).toFloat()
            }
        }
        click(0, 1150.0, 0.55f)
        click(sr, 820.0, 0.5f)
        return ShortArray(out.size) { (out[it].coerceIn(-1f, 1f) * 30000f).toInt().toShort() }
    }

    // ---------------------------------------------------------------- chime

    fun playChime(volume: Float = 0.8f) {
        thread(name = "waqt-chime") {
            val notes = doubleArrayOf(659.25, 783.99, 1046.5)
            val noteLen = (sr * 0.45).toInt()
            val total = noteLen * notes.size + sr / 2
            val pcm = ShortArray(total)
            for ((n, f) in notes.withIndex()) {
                val start = n * noteLen
                for (i in 0 until (noteLen + sr / 2).coerceAtMost(total - start)) {
                    val t = i.toDouble() / sr
                    val env = exp(-t * 3.2) * (if (i < 200) i / 200.0 else 1.0)
                    val s = (sin(2 * PI * f * t) + 0.3 * sin(2 * PI * f * 2 * t)) * env
                    val cur = pcm[start + i].toInt()
                    pcm[start + i] = (cur + s * 9000.0 * volume).toInt().coerceIn(-32000, 32000).toShort()
                }
            }
            val track = try { newTrack(total * 2, AudioTrack.MODE_STATIC) } catch (_: Exception) { return@thread }
            try {
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep((total * 1000L / sr) + 200L)
            } catch (_: Exception) {
            } finally {
                killTrack(track)
            }
        }
    }

    fun release() {
        stopAmbient()
        stopTick()
    }

    private fun killTrack(t: AudioTrack?) {
        if (t == null) return
        try { t.pause() } catch (_: Exception) {}
        try { t.flush() } catch (_: Exception) {}
        try { t.release() } catch (_: Exception) {}
    }
}

// =====================================================================================
// Generators
// =====================================================================================

private class Lp(fc: Float, sr: Int) {
    var a = 1f - exp(-2.0 * PI * fc / sr).toFloat()
    var y = 0f
    fun set(fc: Float, sr: Int) { a = 1f - exp(-2.0 * PI * fc / sr).toFloat() }
    fun p(x: Float): Float { y += a * (x - y); return y }
}

private class Voices(private val max: Int, private val sr: Int) {
    private val f = FloatArray(max)
    private val ph = FloatArray(max)
    private val env = FloatArray(max)
    private val dec = FloatArray(max)
    private val amp = FloatArray(max)
    private val sweep = FloatArray(max)
    private val kind = IntArray(max) // 0 sine, 1 noise burst
    private var nextSlot = 0
    private val rnd = Random()

    /** decaySec = time for the voice to fall by ~60 dB/3 (≈ e-fold *3) ; sweepRatio = fractional pitch change over life */
    fun spawn(freq: Float, a: Float, decaySec: Float, sweepRatio: Float, noise: Boolean) {
        val i = nextSlot
        nextSlot = (nextSlot + 1) % max
        f[i] = freq
        ph[i] = 0f
        env[i] = 1f
        amp[i] = a
        dec[i] = exp(-3.0 / (decaySec * sr)).toFloat()
        sweep[i] = freq * sweepRatio / (decaySec * sr)
        kind[i] = if (noise) 1 else 0
    }

    fun tick(): Float {
        var s = 0f
        for (i in 0 until max) {
            val e = env[i]
            if (e < 0.002f) continue
            if (kind[i] == 0) {
                ph[i] += (2.0 * PI * f[i] / sr).toFloat()
                f[i] += sweep[i]
                s += sin(ph[i]) * e * amp[i]
            } else {
                s += (rnd.nextFloat() * 2f - 1f) * e * amp[i]
            }
            env[i] = e * dec[i]
        }
        return s
    }
}

private class Reverb(sr: Int, variant: Int) {
    private val combDelays = intArrayOf(
        (sr * (0.0297 + 0.0011 * variant)).toInt(), (sr * (0.0371 + 0.0013 * variant)).toInt(),
        (sr * (0.0411 + 0.0009 * variant)).toInt(), (sr * (0.0437 + 0.0015 * variant)).toInt()
    )
    private val combBuf = Array(4) { FloatArray(combDelays[it]) }
    private val combIdx = IntArray(4)
    private val lpState = FloatArray(4)
    private val apDelays = intArrayOf((sr * 0.0050).toInt() + variant * 7, (sr * 0.0017).toInt() + variant * 3)
    private val apBuf = Array(2) { FloatArray(apDelays[it]) }
    private val apIdx = IntArray(2)

    fun process(x: Float): Float {
        var sum = 0f
        for (c in 0 until 4) {
            val b = combBuf[c]
            val i = combIdx[c]
            val y = b[i]
            lpState[c] += 0.35f * (y - lpState[c]) // damping: dark, natural tail
            b[i] = x + lpState[c] * 0.80f
            combIdx[c] = (i + 1) % b.size
            sum += y
        }
        var out = sum * 0.25f
        for (a in 0 until 2) {
            val b = apBuf[a]
            val i = apIdx[a]
            val d = b[i]
            val y = -out + d
            b[i] = out + d * 0.5f
            apIdx[a] = (i + 1) % b.size
            out = y
        }
        return out
    }
}

private class Talker(private val sr: Int, private val rnd: Random, private val level: Float) {
    private var f0 = 110f + rnd.nextFloat() * 110f
    private var phase = 0f
    private var env = 0f
    private var target = 0f
    private var left = 0
    private val f1 = Lp(500f + rnd.nextFloat() * 300f, sr)
    private val f1b = Lp(300f, sr)
    private val f2 = Lp(1500f + rnd.nextFloat() * 800f, sr)
    private val f2b = Lp(900f, sr)

    fun next(): Float {
        if (left <= 0) {
            val talking = rnd.nextFloat() < 0.62f
            target = if (talking) 0.4f + 0.6f * rnd.nextFloat() else 0f
            left = ((0.09f + rnd.nextFloat() * 0.22f) * sr).toInt()
            if (talking) { f0 = (f0 * 0.9f + (100f + rnd.nextFloat() * 130f) * 0.1f); f1.set(450f + rnd.nextFloat() * 450f, sr); f2.set(1300f + rnd.nextFloat() * 1300f, sr) }
        }
        left--
        env += (target - env) * 0.0012f
        phase += f0 / sr
        if (phase > 1f) phase -= 1f
        val saw = phase * 2f - 1f
        val v1 = f1.p(saw) - f1b.p(saw)
        val v2 = f2.p(saw) - f2b.p(saw)
        return (v1 * 1.6f + v2 * 1.1f) * env * level
    }
}

private class AmbientGenerator(private val kind: Int, private val sr: Int, startSample: Long, seed: Long, private val ch: Int) {
    private val rnd = Random(seed)
    private var n = startSample
    private val voices = Voices(40, sr)
    private val reverb = Reverb(sr, ch)

    // pink noise state
    private var b0 = 0f; private var b1 = 0f; private var b2 = 0f; private var b3 = 0f
    private var b4 = 0f; private var b5 = 0f; private var b6 = 0f
    private var brown = 0f

    private val lpA = Lp(200f, sr); private val lpB = Lp(2500f, sr); private val lpC = Lp(900f, sr)
    private val lpD = Lp(6000f, sr); private val lpE = Lp(1500f, sr); private val lpF = Lp(120f, sr)
    private val windLp = Lp(400f, sr)
    private var windCounter = 0
    private val talkers = List(6) { Talker(sr, rnd, 0.05f) }
    private var chirpTimer = 0
    private val twoPi = (2.0 * PI)

    private fun white() = rnd.nextFloat() * 2f - 1f

    private fun pink(): Float {
        val w = white()
        b0 = 0.99886f * b0 + w * 0.0555179f
        b1 = 0.99332f * b1 + w * 0.0750759f
        b2 = 0.96900f * b2 + w * 0.1538520f
        b3 = 0.86650f * b3 + w * 0.3104856f
        b4 = 0.55000f * b4 + w * 0.5329522f
        b5 = -0.7616f * b5 - w * 0.0168980f
        val out = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362f
        b6 = w * 0.115926f
        return out * 0.11f
    }

    private fun rate(perSecond: Float) = rnd.nextFloat() < perSecond / sr
    private fun sinT(freq: Double, t: Double) = sin(twoPi * freq * t).toFloat()
    private fun r(a: Float, b: Float) = a + (b - a) * rnd.nextFloat()

    private fun wind(t: Double, depth: Float): Float {
        if (windCounter++ % 48 == 0) {
            val g = 0.5f + 0.5f * sinT(0.085, t) * (0.6f + 0.4f * sinT(0.21, t + 1.3))
            windLp.set(180f + 900f * g * g, sr)
        }
        val gust = 0.25f + 0.75f * (0.5f + 0.5f * sinT(0.063, t + 0.7)) * (0.55f + 0.45f * sinT(0.19, t + 2.1))
        return windLp.p(pink() * 3.2f) * gust * depth
    }

    private fun crickets(t: Double, loud: Float): Float {
        var s = 0f
        val freqs = doubleArrayOf(4350.0, 4720.0, 5100.0)
        for ((i, f) in freqs.withIndex()) {
            val burst = (t * (1.6 + i * 0.37) + i * 0.31) % 1.0
            val gateB = if (burst < 0.45) sin(PI * burst / 0.45).toFloat() else 0f
            val pulse = (t * (26.0 + i * 3.0)) % 1.0
            val gateP = if (pulse < 0.55) sin(PI * pulse / 0.55).toFloat() else 0f
            s += sinT(f, t) * gateB * gateP
        }
        return s * loud
    }

    private fun clockTick(t: Double): Float {
        val phase = t % 2.0
        val local = if (phase < 1.0) phase else phase - 1.0
        val f = if (phase < 1.0) 1150.0 else 820.0
        if (local > 0.18) return 0f
        return sinT(f, local) * exp(-local * 38.0).toFloat() * 0.55f + white() * exp(-local * 220.0).toFloat() * 0.12f
    }

    private fun countdown(t: Double): Float {
        val phase = t % 1.0
        val beep = if (phase < 0.12) sinT(880.0, t) * (1.0 - phase / 0.12).toFloat() else 0f
        return beep * 0.25f
    }

    private fun rain(): Float {
        val p = pink()
        val sheet = lpD.p(p) - lpA.p(p)               // 200 Hz .. 6 kHz sheet of rain
        val roar = lpF.p(p) * 1.2f                    // distant low roar
        if (rate(160f)) voices.spawn(r(3200f, 8200f), r(0.03f, 0.11f), r(0.004f, 0.010f), r(0.0f, 0.25f), false)
        if (rate(16f)) voices.spawn(r(650f, 1700f), r(0.05f, 0.13f), r(0.020f, 0.040f), r(0.3f, 0.7f), false)
        return sheet * 1.7f + roar * 0.55f + voices.tick()
    }

    private fun stream(): Float {
        val p = pink()
        val base = lpB.p(p) * 1.5f
        val hiss = (lpD.p(white()) - lpE.p(white())) * 0.05f
        if (rate(95f)) voices.spawn(r(480f, 2200f), r(0.025f, 0.085f), r(0.012f, 0.032f), r(0.35f, 0.9f), false)
        if (rate(30f)) voices.spawn(r(1800f, 4200f), r(0.01f, 0.03f), r(0.006f, 0.014f), r(0.2f, 0.5f), false)
        return base * 0.9f + hiss + voices.tick()
    }

    private fun sea(t: Double): Float {
        val e1 = 0.5f + 0.5f * sinT(1.0 / 9.5, t)
        val e2 = 0.5f + 0.5f * sinT(1.0 / 6.3, t + 1.1)
        val swell = (e1 * e1 * 0.65f + e2 * e2 * 0.35f)
        val p = pink()
        val low = lpC.p(p) * 2.4f
        val foam = (lpD.p(white()) - lpB.p(white())) * swell * swell * swell
        val deep = lpF.p(p) * 2.0f
        return low * (0.25f + 0.95f * swell) + foam * 0.55f + deep * 0.35f
    }

    private fun fire(): Float {
        val p = pink()
        val rumble = lpF.p(p) * 2.6f + lpC.p(p) * 0.35f
        if (rate(24f)) voices.spawn(0f, r(0.12f, 0.55f), r(0.004f, 0.022f), 0f, true)
        if (rate(2.2f)) voices.spawn(0f, r(0.5f, 0.9f), r(0.002f, 0.005f), 0f, true)
        val raw = voices.tick()
        val crackle = raw - lpE.p(raw)                // keep only the bright part
        return rumble * 0.85f + crackle * 0.9f
    }

    private fun library(t: Double): Float {
        val p = pink()
        val room = lpA.p(p) * 1.6f
        if (rate(0.11f)) voices.spawn(0f, r(0.08f, 0.16f), r(0.18f, 0.32f), 0f, true)
        val page = voices.tick().let { it - lpE.p(it) }
        return room * 0.35f + page + sinT(50.0, t) * 0.004f
    }

    private fun wilderness(t: Double): Float {
        if (chirpTimer-- <= 0) {
            chirpTimer = (r(0.4f, 2.2f) * sr).toInt()
            when (rnd.nextInt(3)) {
                0 -> voices.spawn(r(2400f, 3400f), 0.07f, r(0.08f, 0.16f), r(0.5f, 0.9f), false)
                1 -> for (k in 0 until 5) voices.spawn(r(3000f, 3800f) + k * 90f, 0.05f, 0.03f, 0.2f, false)
                else -> { voices.spawn(r(1800f, 2400f), 0.06f, 0.12f, -0.3f, false); voices.spawn(r(2400f, 3000f), 0.04f, 0.10f, 0.4f, false) }
            }
        }
        return wind(t, 0.32f) + voices.tick() * 1.0f
    }

    private fun frogs(t: Double): Float {
        if (rate(1.0f)) {
            val base = r(240f, 420f)
            for (k in 0 until 3) voices.spawn(base * (1f + 0.04f * k), 0.12f, 0.16f, -0.15f, false)
        }
        val tick = voices.tick()
        val am = 0.55f + 0.45f * sinT(38.0, t)
        return crickets(t, 0.025f) + wind(t, 0.12f) + tick * am
    }

    private fun classroom(t: Double): Float {
        var s = 0f
        for (i in 0 until 3) s += talkers[i].next()
        if (rate(0.5f)) voices.spawn(0f, r(0.05f, 0.14f), r(0.04f, 0.09f), 0f, true)
        val thud = voices.tick().let { lpC.p(it) }
        return s * 1.2f + thud * 1.2f + lpA.p(pink()) * 0.4f
    }

    private fun cafe(t: Double): Float {
        var s = 0f
        for (i in 0 until 6) s += talkers[i].next()
        if (rate(0.8f)) voices.spawn(r(2800f, 5200f), r(0.03f, 0.07f), r(0.15f, 0.35f), 0f, false)
        if (rate(0.35f)) voices.spawn(0f, r(0.06f, 0.12f), r(0.05f, 0.12f), 0f, true)
        return s * 1.2f + voices.tick() * 0.9f + lpC.p(pink()) * 0.35f
    }

    fun next(): Float {
        val t = n.toDouble() / sr
        n++
        return when (kind) {
            1 -> clockTick(t)
            2 -> countdown(t)
            else -> {
                val dry = when (kind) {
                    3 -> wind(t, 0.55f) + crickets(t, 0.04f)
                    4 -> classroom(t)
                    5 -> wilderness(t)
                    6 -> stream()
                    7 -> sea(t)
                    8 -> rain()
                    9 -> cafe(t)
                    10 -> fire()
                    11 -> library(t)
                    12 -> wind(t, 1.0f)
                    13 -> frogs(t)
                    14 -> { brown = (brown + 0.02f * white()) / 1.02f; brown * 3.5f * (22050f / sr).pow(0.5f) + pink() * 0.6f }
                    else -> 0f
                }
                // dry/wet room reverb gives air and a natural stereo width
                dry * 0.82f + reverb.process(dry) * 0.38f
            }
        }
    }
}
