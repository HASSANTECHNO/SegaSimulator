package ir.segasim.emu

/**
 * JNI bridge to the vendored Genesis Plus GX libretro core (libsegacore.so).
 *
 * The core is linked statically into the native library (no dlopen and no
 * RetroArch dependency). All calls must happen on the emulator thread.
 *
 * Genesis Plus GX (c) Eke-Eke / Charles MacDonald — non-commercial license.
 * See NOTICE.md at the project root.
 */
object EmulatorCore {

    // --- libretro joypad ids (RETRO_DEVICE_ID_JOYPAD_*) -----------------
    // Genesis Plus GX maps (DEVICE_PAD3B / PAD6B):
    //   libretro B -> Mega Drive B     libretro A -> Mega Drive C
    //   libretro Y -> Mega Drive A     libretro START -> START
    const val BTN_B      = 0   // MD "B"
    const val BTN_Y      = 1   // MD "A"
    const val BTN_SELECT = 2
    const val BTN_START  = 3
    const val BTN_UP     = 4
    const val BTN_DOWN   = 5
    const val BTN_LEFT   = 6
    const val BTN_RIGHT  = 7
    const val BTN_A      = 8   // MD "C"
    const val BTN_X      = 9
    const val BTN_L      = 10
    const val BTN_R      = 11
    const val BTN_L2     = 12
    const val BTN_R2     = 13

    /** Encodes pressed buttons into the 16-bit libretro joypad bitfield. */
    fun bitsOf(vararg pressed: Int): Int {
        var bits = 0
        for (b in pressed) bits = bits or (1 shl b)
        return bits
    }

    // --- native methods (implemented in app/src/main/cpp/native-lib.c) ---
    external fun nativeVersion(): Int
    external fun nativeSystemName(): String
    external fun nativeLoadRom(rom: ByteArray, dir: String): Boolean
    external fun nativeRunFrame()
    external fun nativeGetWidth(): Int
    external fun nativeGetHeight(): Int
    external fun nativeReadFrame(out: ShortArray, maxLen: Int): Int
    external fun nativeReadAudio(out: ShortArray, maxFrames: Int): Int

    /** pad: 0 = بازیکن ۱، 1 = بازیکن ۲ (HotSeat روی همین دستگاه) */
    external fun nativeSetInput(pad: Int, bits: Int)

    external fun nativeSerializeSize(): Int
    external fun nativeSaveState(out: ByteArray): Boolean
    external fun nativeLoadState(data: ByteArray): Boolean
    external fun nativeReset()

    init {
        System.loadLibrary("segacore")
    }
}
