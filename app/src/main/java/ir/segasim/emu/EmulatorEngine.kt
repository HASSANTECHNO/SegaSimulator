package ir.segasim.emu

import ir.segasim.catalog.PlayerMode
import ir.segasim.net.NetSync

/**
 * The emulator engine: owns the core thread, paces frames at the core's
 * FPS (~59.92 NTSC / 50.0 PAL), feeds the audio ring to AudioTrack and
 * routes input by [PlayerMode]:
 *
 *  - Single  : pad0 = local input (merged with netplay peer if present).
 *  - HotSeat : pad0 = player-1 touch pad, pad1 = player-2 touch pad,
 *              both on the same device. No game code is duplicated —
 *              only which pad the bits are written to changes.
 *
 * Video: RGB565 frames exposed via [lastFrame]/[lastWidth]/[lastHeight].
 * Audio: s16 stereo @44100 drained from the native ring on the audio thread.
 */
class EmulatorEngine(
    private val net: NetSync? = null,
    private val mode: PlayerMode = PlayerMode.Single,
    private val listener: Listener? = null,
) {
    interface Listener {
        fun onFrameReady(width: Int, height: Int)
        fun onDesync(frame: Long)
    }

    @Volatile var running = false
        private set

    /** آخرین فریم RGB565 (آرایه مشترک؛ UI آن را در بیت‌مپ کپی می‌کند) */
    @Volatile var lastFrame: ShortArray? = null
        private set
    @Volatile var lastWidth = 0
        private set
    @Volatile var lastHeight = 0
        private set

    /** Input bitfield per pad: [0] = بازیکن ۱، [1] = بازیکن ۲ */
    @Volatile var padBits = intArrayOf(0, 0)

    private var frameCounter = 0L
    private var lastHash = 0L
    private val frame = ShortArray(720 * 576)
    private val audioBuf = ShortArray(2048 * 2)

    companion object {
        const val DESYNC_CHECK_INTERVAL = 600L
    }

    /** Loads a ROM. `dir` is used by the core as system/save directory. */
    fun loadRom(rom: ByteArray, dir: String): Boolean =
        EmulatorCore.nativeLoadRom(rom, dir)

    fun start() {
        if (running) return
        running = true
        Thread({
            val frameNanos = (1000.0 / 59.92 * 1_000_000).toLong() // NTSC MD
            var next = System.nanoTime()
            while (running) {
                step()
                next += frameNanos
                val now = System.nanoTime()
                if (next > now) {
                    Thread.sleep((next - now) / 1_000_000)
                } else {
                    next = now // عقب‌افتادگی: بدون بدهی خواب ادامه بده
                }
            }
        }, "emu-core").start()
    }

    fun stop() {
        running = false
    }

    private fun step() {
        frameCounter++

        // --- مسیریابی ورودی بر اساس حالت بازی ------------------------------
        when (mode) {
            PlayerMode.Single -> {
                var pad0 = padBits[0]
                net?.tick(frameCounter, pad0)?.let { remote ->
                    if (remote != 0) pad0 = pad0 or remote
                }
                EmulatorCore.nativeSetInput(0, pad0)
                EmulatorCore.nativeSetInput(1, 0)
            }
            PlayerMode.HotSeat -> {
                // هر دو پد فعال‌اند؛ UI تعیین می‌کند کدام لمس کدام بازیکن است
                EmulatorCore.nativeSetInput(0, padBits[0])
                EmulatorCore.nativeSetInput(1, padBits[1])
            }
        }

        // --- شبیه‌سازی یک فریم ----------------------------------------------
        EmulatorCore.nativeRunFrame()

        // --- تحویل فریم به UI ------------------------------------------------
        val n = EmulatorCore.nativeReadFrame(frame, frame.size)
        if (n > 0) {
            lastWidth = EmulatorCore.nativeGetWidth()
            lastHeight = EmulatorCore.nativeGetHeight()
            lastFrame = frame
            listener?.onFrameReady(lastWidth, lastHeight)
        }

        // --- تشخیص دیسنک (فقط نت‌پلی) ----------------------------------------
        if (net != null && frameCounter % DESYNC_CHECK_INTERVAL == 0L) {
            val size = EmulatorCore.nativeSerializeSize()
            if (size > 0) {
                val st = ByteArray(size)
                if (EmulatorCore.nativeSaveState(st)) {
                    var h = 1125899906842597L
                    for (i in st.indices step 97) h = h * 31 + st[i]
                    if (lastHash != 0L && h != lastHash) listener?.onDesync(frameCounter)
                    lastHash = h
                }
            }
        }
    }

    /** Called from the AudioTrack write loop on the audio thread. */
    fun drainAudio(): ShortArray {
        val n = EmulatorCore.nativeReadAudio(audioBuf, 2048)
        return if (n > 0) audioBuf.copyOf(n * 2) else ShortArray(0)
    }

    fun reset() {
        EmulatorCore.nativeReset()
        frameCounter = 0
        lastHash = 0
    }
}
