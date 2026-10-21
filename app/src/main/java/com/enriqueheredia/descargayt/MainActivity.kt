package com.enriqueheredia.descargayt

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Descarga YT - App creada por Enrique Heredia
 * Interfaz web (assets/index.html) + motor yt-dlp/FFmpeg empaquetado dentro de la APK.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private val hilos = Executors.newCachedThreadPool()
    private val ocupado = AtomicBoolean(false)
    private val motorListo = CountDownLatch(1)
    @Volatile private var motorError: String? = null
    private var paginaCargada = false
    private var urlPendiente: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fondo = Color.parseColor("#1E1F26")
        window.statusBarColor = fondo
        window.navigationBarColor = fondo

        web = WebView(this)
        web.setBackgroundColor(fondo)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                paginaCargada = true
                urlPendiente?.let { enviarEntrada(it); urlPendiente = null }
            }
        }
        web.addJavascriptInterface(Puente(), "Android")
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")

        urlPendiente = extraerUrl(intent)
        iniciarMotor()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extraerUrl(intent)?.let { if (paginaCargada) enviarEntrada(it) else urlPendiente = it }
    }

    // ---------- utilidades hacia la página ----------
    private fun js(codigo: String) = runOnUiThread { web.evaluateJavascript(codigo, null) }
    private fun log(texto: String, error: Boolean = false) = js("ytLog(${JSONObject.quote(texto)},$error)")
    private fun enviarEntrada(url: String) = js("ytEntrada(${JSONObject.quote(url)})")

    private fun extraerUrl(i: Intent?): String? {
        if (i?.action != Intent.ACTION_SEND) return null
        val texto = i.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return Regex("https?://\\S+").find(texto)?.value
    }

    // ---------- motor ----------
    private fun iniciarMotor() {
        hilos.execute {
            try {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                actualizarMotor()
            } catch (e: Exception) {
                motorError = e.message ?: e.toString()
                log("No se pudo iniciar el motor: $motorError", true)
            } finally {
                motorListo.countDown()
            }
        }
    }

    /** YouTube cambia a menudo: se actualiza yt-dlp como mucho cada 12 horas. */
    private fun actualizarMotor() {
        val prefs = getSharedPreferences("motor", Context.MODE_PRIVATE)
        val ahora = System.currentTimeMillis()
        if (ahora - prefs.getLong("ultima", 0L) < 12L * 3600 * 1000) return
        try {
            log("Comprobando actualizaciones del motor…")
            YoutubeDL.getInstance().updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.STABLE)
            prefs.edit().putLong("ultima", ahora).apply()
            log("Motor al día.")
        } catch (e: Exception) {
            log("No se pudo actualizar el motor (¿sin internet?). Se usa la versión incluida.")
        }
    }

    // ---------- descarga ----------
    private fun descargar(url: String, calidad: String) {
        if (!ocupado.compareAndSet(false, true)) {
            js("ytError(${JSONObject.quote("Ya hay una descarga en curso.")})")
            return
        }
        hilos.execute {
            val carpeta = File(cacheDir, "dl/" + System.currentTimeMillis()).apply { mkdirs() }
            try {
                log("Preparando motor…")
                motorListo.await()
                motorError?.let { throw IllegalStateException(it) }

                val req = YoutubeDLRequest(url)
                req.addOption("--no-playlist")
                req.addOption("--no-mtime")
                req.addOption("--windows-filenames")
                req.addOption("--cache-dir", File(cacheDir, "ytdlp").absolutePath)
                req.addOption("-o", carpeta.absolutePath + "/%(title).150B.%(ext)s")

                val res = mapOf("max" to "res", "1080" to "res:1080", "720" to "res:720", "480" to "res:480")[calidad]
                when {
                    res != null -> {
                        req.addOption("-S", "$res,vcodec:h264,acodec:m4a")
                        req.addOption("-f", "bv*+ba/b")
                        req.addOption("--merge-output-format", "mp4")
                    }
                    calidad == "mp3" -> {
                        req.addOption("-f", "ba/b")
                        req.addOption("-x")
                        req.addOption("--audio-format", "mp3")
                        req.addOption("--audio-quality", "0")
                    }
                    calidad == "m4a" -> {
                        req.addOption("-f", "ba[ext=m4a]/ba/b")
                        req.addOption("-x")
                        req.addOption("--audio-format", "m4a")
                    }
                    else -> throw IllegalArgumentException("Calidad desconocida: $calidad")
                }

                // Motor JavaScript (QuickJS) que YouTube necesita; lo compila el workflow de GitHub
                val qjs = File(applicationInfo.nativeLibraryDir, "libqjs.so")
                if (qjs.exists()) req.addOption("--js-runtimes", "quickjs:" + qjs.absolutePath)

                log("Descargando…")
                YoutubeDL.getInstance().execute(req, "descarga", callback = { progreso, _, linea ->
                    val p = progreso.toInt()
                    if (p in 0..100) js("ytProgreso($p)")
                    val esProgreso = linea.startsWith("[download]") && linea.contains("%")
                    if (!esProgreso && linea.isNotBlank()) log(linea.trim())
                })

                val archivo = carpeta.listFiles()
                    ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                    ?.maxByOrNull { it.length() }
                    ?: throw IllegalStateException("yt-dlp terminó pero no se creó ningún archivo.")

                val nombre = guardarEnDescargas(archivo)
                js("ytProgreso(100)")
                js("ytFin(${JSONObject.quote(nombre)})")
            } catch (e: Exception) {
                val msg = (e.message ?: e.toString()).trim().takeLast(600)
                js("ytError(${JSONObject.quote(msg)})")
            } finally {
                carpeta.deleteRecursively()
                ocupado.set(false)
            }
        }
    }

    /** Copia el archivo a Descargas/DescargaYT (sin pedir permisos, Android 10+). */
    private fun guardarEnDescargas(f: File): String {
        val mime = when (f.extension.lowercase()) {
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            else -> "application/octet-stream"
        }
        val valores = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DescargaYT")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
            ?: throw IllegalStateException("No se pudo crear el archivo en Descargas.")
        val salida = contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("No se pudo escribir en Descargas.")
        salida.use { out -> f.inputStream().use { it.copyTo(out) } }
        valores.clear()
        valores.put(MediaStore.MediaColumns.IS_PENDING, 0)
        contentResolver.update(uri, valores, null, null)
        return f.name
    }

    // ---------- puente JavaScript ----------
    inner class Puente {
        @JavascriptInterface
        fun iniciar(url: String, calidad: String) = descargar(url, calidad)

        @JavascriptInterface
        fun portapapeles(): String {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return ""
            if (clip.itemCount == 0) return ""
            return clip.getItemAt(0).coerceToText(this@MainActivity)?.toString() ?: ""
        }
    }
}
