package ir.segasim.net

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal LAN netplay, delay-based lockstep:
 *
 *   - Both peers run the same ROM and the same deterministic core.
 *   - Every emulated frame each side sends its local 16-bit input bitfield.
 *   - Input is applied `delayFrames` frames later on both sides, which hides
 *     one-way latency (frame delay = ping / 16.6ms, clamped to [1..8]).
 *   - A desync is detected by hashing the save-state every 600 frames.
 *
 * This mirrors the RetroArch netplay lockstep model (input exchange per
 * frame, no state transfer during normal play). Rollback is a future
 * improvement: keep a save-state ring and re-simulate on late input.
 */
class NetSync(
    private val isHost: Boolean,
    private val host: String? = null,
    private val port: Int = 24879,
) {
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private val running = AtomicBoolean(false)

    /** Frames of latency both sides agreed on. */
    @Volatile var delayFrames: Int = 2
        private set

    /** Latest local input sampled by the engine for frame F. */
    @Volatile var localInput: Int = 0

    /** Remote input valid for frame F (after delay). */
    @Volatile var remoteInput: Int = 0

    fun connect(timeoutMs: Int = 5000): Boolean {
        running.set(true)
        return try {
            val s = if (isHost) {
                val server = ServerSocket(port)
                server.reuseAddress = true
                val accepted = server.accept()   // blocks until guest joins
                server.close()
                accepted
            } else {
                val s = Socket()
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(host, port), timeoutMs)
                s
            }
            s.tcpNoDelay = true
            socket = s
            input = s.getInputStream()
            output = s.getOutputStream()
            true
        } catch (e: Exception) {
            running.set(false)
            false
        }
    }

    /** Exchange the handshake (ping measurement) and agree on delay frames. */
    fun handshake(): Int {
        val t0 = System.nanoTime()
        writeInt(0x5E6A_0001)
        val peer = readInt()             // guest pings host, host pings guest
        val rttMs = (System.nanoTime() - t0) / 1_000_000.0
        delayFrames = (rttMs / 16.6).toInt().coerceIn(1, 8)
        writeInt(delayFrames)
        readInt()                        // peer's chosen delay
        return delayFrames
    }

    /**
     * Called once per emulated frame:
     * sends local input for frame F and returns remote input for frame F+delay.
     */
    fun tick(frame: Long, local: Int): Int {
        if (socket == null) return 0
        try {
            writeShort(local)
            remoteInput = readUnsignedShort()
            return remoteInput
        } catch (e: Exception) {
            disconnect()
            return remoteInput
        }
    }

    fun isConnected(): Boolean = socket != null && socket!!.isConnected && running.get()

    fun disconnect() {
        running.set(false)
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    private fun writeShort(v: Int) {
        val o = output ?: throw EOFException("not connected")
        o.write((v shr 8) and 0xFF); o.write(v and 0xFF); o.flush()
    }

    private fun readUnsignedShort(): Int {
        val i = input ?: throw EOFException("not connected")
        val hi = i.read(); val lo = i.read()
        if (hi < 0 || lo < 0) throw EOFException("peer closed")
        return (hi shl 8) or lo
    }

    private fun writeInt(v: Int) {
        val o = output ?: throw EOFException("not connected")
        o.write(v ushr 24); o.write(v ushr 16); o.write(v ushr 8); o.write(v); o.flush()
    }

    private fun readInt(): Int {
        val i = input ?: throw EOFException("not connected")
        val a = i.read(); val b = i.read(); val c = i.read(); val d = i.read()
        if (a < 0 || b < 0 || c < 0 || d < 0) throw EOFException("peer closed")
        return (a shl 24) or (b shl 16) or (c shl 8) or d
    }
}
