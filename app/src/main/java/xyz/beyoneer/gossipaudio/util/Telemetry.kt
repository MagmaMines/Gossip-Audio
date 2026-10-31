package xyz.beyoneer.gossipaudio.util

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.os.StatFs
import java.io.File
import java.io.RandomAccessFile

/**
 * Real device telemetry — no fake numbers. Everything shown on the
 * "Neural Load" panel comes from /proc, ActivityManager or EGL.
 */
class Telemetry(private val context: Context) {

    data class Snapshot(
        val cpuPercent: Float,
        val cores: Int,
        val usedMemMb: Long,
        val totalMemMb: Long,
        val appMemMb: Long,
        val maxHeapMb: Long,
        val freeStorageMb: Long,
        val gpu: String,
        val gles: String,
        val nnapi: Boolean,
        val soc: String,
        val android: String
    )

    private var lastTotal = 0L
    private var lastIdle = 0L
    private val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun snapshot(): Snapshot {
        val cpu = readCpuPercent()
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val rt = Runtime.getRuntime()
        val stat = StatFs(context.filesDir.absolutePath)
        val freeMb = stat.availableBlocksLong * stat.blockSizeLong / (1024 * 1024)
        val deviceConfig = am.deviceConfigurationInfo
        return Snapshot(
            cpuPercent = cpu,
            cores = Runtime.getRuntime().availableProcessors(),
            usedMemMb = (mi.totalMem - mi.availMem) / (1024 * 1024),
            totalMemMb = mi.totalMem / (1024 * 1024),
            appMemMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024),
            maxHeapMb = rt.maxMemory() / (1024 * 1024),
            freeStorageMb = freeMb,
            gpu = GPU_RENDERER ?: detectGpu(),
            gles = deviceConfig.glEsVersion ?: "2.0",
            nnapi = Build.VERSION.SDK_INT >= 27 && context.packageManager.hasSystemFeature("android.hardware.neuralnetworks"),
            soc = if (Build.VERSION.SDK_INT >= 31) (Build.SOC_MODEL ?: Build.HARDWARE) else Build.HARDWARE,
            android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        )
    }

    fun deviceFacts(): List<Pair<String, String>> {
        val s = snapshot()
        return listOf(
            "Device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "SoC" to s.soc,
            "Android" to s.android,
            "CPU" to "${s.cores} cores · ${"%.0f".format(s.cpuPercent)}% busy",
            "RAM" to "${s.usedMemMb} / ${s.totalMemMb} MB",
            "GPU" to s.gpu,
            "OpenGL ES" to s.gles,
            "NN API" to if (s.nnapi) "available (accelerated voices)" else "not advertised",
            "Free storage" to "${s.freeStorageMb} MB",
            "App heap" to "${s.appMemMb} / ${s.maxHeapMb} MB"
        )
    }

    private fun readCpuPercent(): Float {
        try {
            RandomAccessFile("/proc/stat", "r").use { file ->
                val line = file.readLine() ?: return 0f
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 5) return 0f
                var total = 0L
                for (i in 1 until parts.size) total += parts[i].toLongOrNull() ?: 0L
                val idle = (parts[4].toLongOrNull() ?: 0L) + (parts.getOrNull(5)?.toLongOrNull() ?: 0L)
                val dTotal = total - lastTotal
                val dIdle = idle - lastIdle
                lastTotal = total
                lastIdle = idle
                if (dTotal <= 0) return 0f
                return ((dTotal - dIdle).toFloat() / dTotal * 100f).coerceIn(0f, 100f)
            }
        } catch (_: Throwable) {
            return 0f
        }
    }

    private fun detectGpu(): String {
        val detected = try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY) ?: return "not reported"
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return "not reported"
            val attribs = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE)
            val configs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0)) return "not reported"
            val ctx = EGL14.eglCreateContext(
                display, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
            )
            if (ctx == null || ctx == EGL14.EGL_NO_CONTEXT) return "not reported"
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, ctx)
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "not reported"
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroyContext(display, ctx)
            EGL14.eglTerminate(display)
            renderer
        } catch (_: Throwable) {
            "not reported"
        }
        GPU_RENDERER = detected
        return detected
    }

    /** Samples CPU + RAM once per interval while a synthesis job runs. */
    class Sampler(private val telemetry: Telemetry, private val intervalMs: Long = 350) {
        private val values = ArrayList<Pair<Float, Float>>()
        @Volatile private var running = false
        private var thread: Thread? = null

        fun start() {
            if (running) return
            running = true
            thread = Thread {
                while (running) {
                    try {
                        val s = telemetry.snapshot()
                        val memPercent = if (s.totalMemMb <= 0) 0f else s.usedMemMb.toFloat() / s.totalMemMb * 100f
                        synchronized(values) { values.add(s.cpuPercent to memPercent) }
                    } catch (_: Throwable) {
                    }
                    try {
                        Thread.sleep(intervalMs)
                    } catch (_: InterruptedException) {
                        return@Thread
                    }
                }
            }
            thread?.isDaemon = true
            thread?.start()
        }

        fun stop() {
            running = false
            thread?.interrupt()
            thread = null
        }

        fun samples(): List<Pair<Float, Float>> = synchronized(values) { values.toList() }

        fun peakCpu(): Float = samples().maxOfOrNull { it.first } ?: 0f
        fun peakMem(): Float = samples().maxOfOrNull { it.second } ?: 0f
    }

    companion object {
        @Volatile private var GPU_RENDERER: String? = null

        fun cacheDirSize(dir: File): Long {
            var total = 0L
            dir.listFiles()?.forEach { total += if (it.isDirectory) cacheDirSize(it) else it.length() }
            return total
        }
    }
}
