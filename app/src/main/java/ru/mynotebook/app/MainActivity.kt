package ru.mynotebook.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import java.io.File
import android.util.Base64

class MainActivity : ComponentActivity() {
    private lateinit var web: WebView
    private lateinit var status: TextView
    private var cameraUri: Uri? = null
    private val notesFile by lazy { File(filesDir, "notebook.html") }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { insertImageFromUri(it) }
    }
    private val choosePhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { insertImageFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(247, 245, 236)
        window.navigationBarColor = Color.rgb(247, 245, 236)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setBackgroundColor(Color.rgb(247, 245, 236))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(10))
        }
        val title = TextView(this).apply {
            text = "Мой блокнот"
            textSize = 25f
            setTextColor(Color.rgb(35, 53, 41))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val subtitle = TextView(this).apply {
            text = "Ваши записи — только на этом устройстве"
            textSize = 13f
            setTextColor(Color.rgb(102, 110, 96))
            setPadding(0, dp(3), 0, dp(8))
        }
        header.addView(title)
        header.addView(subtitle)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(makeButton("＋ Фото", Color.rgb(220, 229, 210)) { choosePhoto.launch("image/*") }, weightParams())
        actions.addView(makeButton("📷 Камера", Color.rgb(220, 229, 210)) { openCamera() }, weightParams())
        actions.addView(makeButton("Сохранить", Color.rgb(48, 79, 54), Color.WHITE) { web.evaluateJavascript("saveNow()", null); toast("Запись сохранена") }, weightParams())
        header.addView(actions)
        root.addView(header)

        status = TextView(this).apply {
            text = "Автосохранение включено"
            textSize = 12f
            setTextColor(Color.rgb(105, 112, 99))
            setPadding(dp(18), dp(5), dp(18), dp(5))
        }
        web = WebView(this).apply {
            setBackgroundColor(Color.rgb(247, 245, 236))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            addJavascriptInterface(EditorBridge(), "AndroidNote")
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(status)
        setContentView(root)
        web.loadUrl("file:///android_asset/editor.html")
    }

    private fun openCamera() {
        try {
            val dir = File(cacheDir, "camera").apply { mkdirs() }
            val photo = File(dir, "photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "ru.mynotebook.app.fileprovider", photo)
            cameraUri = uri
            takePhoto.launch(uri)
        } catch (e: Exception) {
            toast("Не удалось открыть камеру: ${e.localizedMessage ?: "ошибка"}")
        }
    }

    private fun insertImageFromUri(uri: Uri) {
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
            if (bytes.size > 12 * 1024 * 1024) {
                toast("Фото слишком большое. Выберите файл до 12 МБ.")
                return
            }
            val mime = contentResolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val js = "insertImage('data:${mime};base64,${encoded}')"
            web.evaluateJavascript(js, null)
            toast("Фото добавлено в запись")
        } catch (e: Exception) {
            toast("Не удалось добавить фото")
        }
    }

    private inner class EditorBridge {
        @JavascriptInterface fun saveHtml(html: String) {
            runOnUiThread { status.text = "Сохранено на устройстве" }
            try { notesFile.writeText(html, Charsets.UTF_8) } catch (_: Exception) {
                runOnUiThread { status.text = "Не удалось сохранить запись" }
            }
        }
        @JavascriptInterface fun loadHtml(): String {
            return try { if (notesFile.exists()) notesFile.readText(Charsets.UTF_8) else "" } catch (_: Exception) { "" }
        }
    }

    private fun makeButton(label: String, bg: Int, fg: Int = Color.rgb(35, 53, 41), click: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(fg)
        backgroundTintList = android.content.res.ColorStateList.valueOf(bg)
        setOnClickListener { click() }
    }
    private fun weightParams() = LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(5) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
