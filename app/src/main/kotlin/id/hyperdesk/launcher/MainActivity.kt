package id.hyperdesk.launcher

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.min

private data class DesktopApp(val label: String, val packageName: String, val launchIntent: Intent)

class MainActivity : Activity() {
    private val backgroundTop = Color.rgb(12, 47, 83)
    private val backgroundBottom = Color.rgb(10, 21, 39)
    private val panel = Color.rgb(25, 35, 51)
    private val panelRaised = Color.rgb(36, 50, 70)
    private val accent = Color.rgb(74, 150, 245)
    private lateinit var root: FrameLayout
    private lateinit var desktop: FrameLayout
    private lateinit var windowsLayer: FrameLayout
    private lateinit var taskbar: LinearLayout
    private lateinit var startPanel: LinearLayout
    private val openWindows = linkedMapOf<String, FrameLayout>()
    private val windowTasks = linkedMapOf<String, View>()
    private var installedApps: List<DesktopApp> = emptyList()
    private val prefs by lazy { getSharedPreferences("hyperdesk", MODE_PRIVATE) }
    private var windowCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = backgroundTop
        window.navigationBarColor = Color.rgb(12, 20, 32)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        refreshApps()
        createShell()
        openWindow("Welcome", ::welcomeWindow, widthFraction = .84, heightFraction = .60)
    }

    override fun onResume() {
        super.onResume()
        refreshApps()
    }

    @Suppress("DEPRECATION")
    private fun refreshApps() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        installedApps = packageManager.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
            .mapNotNull { info ->
                val packageName = info.activityInfo.packageName
                val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return@mapNotNull null
                DesktopApp(info.loadLabel(packageManager).toString(), packageName, launch)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun createShell() {
        root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(backgroundTop, Color.rgb(24, 83, 129), backgroundBottom)
            )
        }
        desktop = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(backgroundTop, Color.rgb(23, 80, 125), backgroundBottom)
            )
        }
        windowsLayer = FrameLayout(this)
        root.addView(desktop, FrameLayout.LayoutParams(-1, -1))
        root.addView(windowsLayer, FrameLayout.LayoutParams(-1, -1))
        buildDesktopIcons()
        buildTaskbar()
        buildStartPanel()
        root.addView(taskbar, FrameLayout.LayoutParams(-1, dp(58), Gravity.BOTTOM))
        root.addView(startPanel, FrameLayout.LayoutParams(panelWidth(), panelHeight(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(66)
        })
        startPanel.visibility = View.GONE
        setContentView(root)
    }

    private fun buildDesktopIcons() {
        val icons = listOf(
            Triple("🗂", "File Explorer", ::explorerWindow),
            Triple("🌐", "Browser", ::browserWindow),
            Triple("📝", "Notepad", ::notepadWindow),
            Triple("⌨", "Terminal", ::terminalWindow),
            Triple("▦", "Task Manager", ::taskManagerWindow),
            Triple("⚙", "Settings", ::settingsWindow)
        )
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(12), 0, 0) }
        icons.forEach { (icon, name, opener) ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(5), dp(4), dp(5))
                background = rounded(Color.TRANSPARENT, 10)
                setOnClickListener { opener() }
                setOnLongClickListener { showShortcutMenu(name) { opener() }; true }
            }
            tile.addView(label(icon, 25, Color.WHITE, false).apply { gravity = Gravity.CENTER })
            tile.addView(label(name, 10, Color.WHITE, false).apply { gravity = Gravity.CENTER; maxLines = 2 })
            column.addView(tile, LinearLayout.LayoutParams(dp(78), dp(72)).apply { bottomMargin = dp(4) })
        }
        desktop.addView(column, FrameLayout.LayoutParams(dp(86), -2, Gravity.TOP or Gravity.LEFT))
        val welcome = label("HyperDesk", 17, 0xFFEAF4FF.toInt(), true).apply {
            gravity = Gravity.TOP or Gravity.RIGHT
            setPadding(0, dp(15), dp(16), 0)
        }
        desktop.addView(welcome, FrameLayout.LayoutParams(-2, dp(40), Gravity.TOP or Gravity.RIGHT))
    }

    private fun buildTaskbar() {
        taskbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(10), dp(6))
            background = rounded(0xF2141E2B.toInt(), 0)
            elevation = dp(18).toFloat()
        }
        val start = label("⊞", 25, 0xFFF8FBFF.toInt(), true).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.TRANSPARENT, 10)
            setOnClickListener { toggleStartMenu() }
        }
        taskbar.addView(start, LinearLayout.LayoutParams(dp(48), -1))
        val search = label("⌕   Cari", 13, 0xFFE3EAF4.toInt(), false).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), 0, 0, 0)
            background = rounded(0xFF273448.toInt(), 12)
            setOnClickListener { appDrawerWindow() }
        }
        taskbar.addView(search, LinearLayout.LayoutParams(0, dp(38), 1f).apply { setMargins(dp(5), 0, dp(6), 0) })
        val quick = label("🗂", 17, Color.WHITE, false).apply { gravity = Gravity.CENTER; setOnClickListener { explorerWindow() } }
        taskbar.addView(quick, LinearLayout.LayoutParams(dp(34), dp(42)))
        val browser = label("🌐", 17, Color.WHITE, false).apply { gravity = Gravity.CENTER; setOnClickListener { browserWindow() } }
        taskbar.addView(browser, LinearLayout.LayoutParams(dp(34), dp(42)))
        val time = label(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()), 11, Color.WHITE, false).apply {
            gravity = Gravity.CENTER
            setOnClickListener { taskManagerWindow() }
        }
        taskbar.addView(time, LinearLayout.LayoutParams(dp(56), dp(42)))
    }

    private fun buildStartPanel() {
        startPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(17), dp(18), dp(14))
            background = rounded(0xF21A2638.toInt(), 22)
            elevation = dp(24).toFloat()
        }
        startPanel.addView(label("Mulai", 21, Color.WHITE, true))
        val search = EditText(this).apply {
            hint = "Cari aplikasi, file, dan pengaturan"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(0xFFABB9CB.toInt())
            background = rounded(0xFF2C3A50.toInt(), 12)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        startPanel.addView(search, LinearLayout.LayoutParams(-1, dp(44)).apply { setMargins(0, dp(13), 0, dp(14)) })
        startPanel.addView(label("Disematkan", 13, 0xFFC3D0E0.toInt(), true))
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val shortcuts = listOf(
            listOf("🌐  Browser" to ::browserWindow, "🗂  File Explorer" to ::explorerWindow),
            listOf("📝  Notepad" to ::notepadWindow, "⌨  Terminal" to ::terminalWindow),
            listOf("▦  Task Manager" to ::taskManagerWindow, "⚙  Settings" to ::settingsWindow)
        )
        shortcuts.forEach { row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { (title, action) ->
                val button = label(title, 12, Color.WHITE, false).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), 0, dp(6), 0)
                    background = rounded(0xFF27364A.toInt(), 12)
                    setOnClickListener { closeStartMenu(); action() }
                }
                line.addView(button, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(0, 0, dp(7), dp(7)) })
            }
            grid.addView(line)
        }
        startPanel.addView(grid)
        val allApps = label("☷   Semua aplikasi", 13, Color.WHITE, false).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, 0, 0)
            background = rounded(0xFF27364A.toInt(), 12)
            setOnClickListener { closeStartMenu(); appDrawerWindow() }
        }
        startPanel.addView(allApps, LinearLayout.LayoutParams(-1, dp(42)).apply { setMargins(0, dp(5), 0, dp(8)) })
        search.addTextChangedListener(TextWatcher { query ->
            if (query.isNotBlank()) {
                closeStartMenu()
                appDrawerWindow(query)
            }
        })
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        footer.addView(label("●  Android Desktop", 12, 0xFFD8E3F0.toInt(), false), LinearLayout.LayoutParams(0, dp(36), 1f))
        footer.addView(label("⏻", 20, Color.WHITE, false).apply {
            gravity = Gravity.CENTER
            setOnClickListener { showPowerMenu() }
        }, LinearLayout.LayoutParams(dp(42), dp(36)))
        startPanel.addView(footer)
    }

    private fun toggleStartMenu() { startPanel.visibility = if (startPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
    private fun closeStartMenu() { startPanel.visibility = View.GONE }

    private fun showShortcutMenu(name: String, opener: () -> Unit) {
        AlertDialog.Builder(this).setItems(arrayOf("Buka $name", "Sematkan ke taskbar")) { _, which ->
            if (which == 0) opener() else addTaskbarShortcut(name, opener)
        }.show()
    }

    private fun addTaskbarShortcut(name: String, opener: () -> Unit) {
        val item = label(name.take(1), 16, Color.WHITE, true).apply { gravity = Gravity.CENTER; setOnClickListener { opener() } }
        taskbar.addView(item, max(1, taskbar.childCount - 1), LinearLayout.LayoutParams(dp(34), dp(42)))
    }

    private fun openWindow(
        title: String,
        contentBuilder: () -> View,
        widthFraction: Double = .88,
        heightFraction: Double = .74,
        key: String = "$title-${windowCounter++}"
    ) {
        closeStartMenu()
        if (openWindows[key] != null) {
            openWindows[key]?.visibility = View.VISIBLE
            openWindows[key]?.bringToFront()
            return
        }
        val width = min((resources.displayMetrics.widthPixels * widthFraction).toInt(), dp(900))
        val height = min((resources.displayMetrics.heightPixels * heightFraction).toInt(), dp(720))
        val frame = FrameLayout(this).apply {
            background = rounded(panel, 17)
            elevation = dp(20).toFloat()
            clipToOutline = true
        }
        val shell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val titlebar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(5), 0)
            background = rounded(0xFF202D40.toInt(), 0)
        }
        titlebar.addView(label("▣  $title", 13, Color.WHITE, true), LinearLayout.LayoutParams(0, -1, 1f))
        val minimize = windowControl("—")
        val maximize = windowControl("□")
        val close = windowControl("×")
        titlebar.addView(minimize, LinearLayout.LayoutParams(dp(38), -1))
        titlebar.addView(maximize, LinearLayout.LayoutParams(dp(38), -1))
        titlebar.addView(close, LinearLayout.LayoutParams(dp(38), -1))
        shell.addView(titlebar, LinearLayout.LayoutParams(-1, dp(42)))
        val content = FrameLayout(this).apply { setBackgroundColor(panel) }
        content.addView(contentBuilder(), FrameLayout.LayoutParams(-1, -1))
        shell.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        frame.addView(shell, FrameLayout.LayoutParams(-1, -1))
        val resize = label("◢", 14, 0xFFB4C6DA.toInt(), false).apply {
            gravity = Gravity.CENTER
            setOnTouchListener(ResizeTouch(frame))
        }
        frame.addView(resize, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.BOTTOM or Gravity.RIGHT))
        val lp = FrameLayout.LayoutParams(width, height, Gravity.TOP or Gravity.LEFT).apply {
            leftMargin = max(dp(12), (resources.displayMetrics.widthPixels - width) / 2)
            topMargin = dp(28 + (openWindows.size % 5) * 22)
        }
        windowsLayer.addView(frame, lp)
        openWindows[key] = frame
        val taskItem = label(title.take(1), 16, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(0xFF26364A.toInt(), 9)
            setOnClickListener {
                frame.visibility = View.VISIBLE
                frame.bringToFront()
                taskbar.bringToFront()
                if (startPanel.visibility == View.VISIBLE) startPanel.bringToFront()
            }
        }
        taskbar.addView(taskItem, max(1, taskbar.childCount - 1), LinearLayout.LayoutParams(dp(34), dp(42)))
        windowTasks[key] = taskItem
        titlebar.setOnTouchListener(DragTouch(frame))
        minimize.setOnClickListener { frame.visibility = View.GONE }
        maximize.setOnClickListener { toggleMaximize(frame) }
        close.setOnClickListener {
            windowsLayer.removeView(frame)
            openWindows.remove(key)
            windowTasks.remove(key)?.let { taskbar.removeView(it) }
        }
        frame.setOnClickListener { frame.bringToFront() }
        taskbar.setOnClickListener { }
    }

    private fun toggleMaximize(frame: FrameLayout) {
        val params = frame.layoutParams as FrameLayout.LayoutParams
        if (frame.getTag(R.id.window_maximized) == true) {
            val saved = frame.getTag(R.id.window_saved_bounds) as? IntArray ?: return
            params.width = saved[0]; params.height = saved[1]; params.leftMargin = saved[2]; params.topMargin = saved[3]
            frame.setTag(R.id.window_maximized, false)
        } else {
            frame.setTag(R.id.window_saved_bounds, intArrayOf(params.width, params.height, params.leftMargin, params.topMargin))
            params.width = root.width - dp(10)
            params.height = root.height - dp(68)
            params.leftMargin = dp(5)
            params.topMargin = dp(5)
            frame.setTag(R.id.window_maximized, true)
        }
        frame.layoutParams = params
        frame.bringToFront()
        taskbar.bringToFront()
        if (startPanel.visibility == View.VISIBLE) startPanel.bringToFront()
    }

    private inner class DragTouch(private val target: View) : View.OnTouchListener {
        private var startX = 0f; private var startY = 0f; private var left = 0; private var top = 0
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val lp = target.layoutParams as? FrameLayout.LayoutParams ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { startX = event.rawX; startY = event.rawY; left = lp.leftMargin; top = lp.topMargin; target.bringToFront(); return true }
                MotionEvent.ACTION_MOVE -> {
                    lp.leftMargin = max(0, left + (event.rawX - startX).toInt())
                    lp.topMargin = max(0, top + (event.rawY - startY).toInt())
                    target.layoutParams = lp
                    return true
                }
            }
            return true
        }
    }

    private inner class ResizeTouch(private val target: View) : View.OnTouchListener {
        private var startX = 0f; private var startY = 0f; private var width = 0; private var height = 0
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val lp = target.layoutParams as? FrameLayout.LayoutParams ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { startX = event.rawX; startY = event.rawY; width = lp.width; height = lp.height; target.bringToFront(); return true }
                MotionEvent.ACTION_MOVE -> {
                    lp.width = max(dp(270), width + (event.rawX - startX).toInt())
                    lp.height = max(dp(230), height + (event.rawY - startY).toInt())
                    lp.width = min(lp.width, root.width - lp.leftMargin)
                    lp.height = min(lp.height, root.height - dp(64) - lp.topMargin)
                    target.layoutParams = lp
                    return true
                }
            }
            return true
        }
    }

    private fun welcomeWindow(): View {
        val body = verticalContent()
        body.addView(label("Selamat datang di HyperDesk", 21, Color.WHITE, true))
        body.addView(label("Desktop Android bergaya Windows, dengan aplikasi yang berjalan di jendela launcher.", 14, 0xFFD0DCEB.toInt(), false).apply { setPadding(0, dp(8), 0, dp(17)) })
        body.addView(label("FITUR DESKTOP", 11, 0xFF9EB0C5.toInt(), true))
        body.addView(label("Jendela dapat dipindah dan diubah ukurannya. Tombol di title bar untuk minimize, maximize, dan close.", 13, 0xFFDFE9F5.toInt(), false).apply { setPadding(0, dp(6), 0, dp(15)) })
        body.addView(action("Buka File Explorer", "Jelajahi file dengan pemilih dokumen Android") { explorerWindow() })
        body.addView(action("Buka browser", "Buka halaman web dalam jendela desktop") { browserWindow() })
        body.addView(action("Buka semua aplikasi", "Cari dan jalankan aplikasi Android") { appDrawerWindow() })
        return ScrollView(this).apply { addView(body) }
    }

    private fun explorerWindow(): View {
        val body = verticalContent()
        body.addView(label("Beranda  /  File Explorer", 17, Color.WHITE, true))
        body.addView(label("Akses file memakai Android Storage Access Framework.", 12, 0xFFBDCAD9.toInt(), false).apply { setPadding(0, dp(5), 0, dp(14)) })
        body.addView(action("📂  Buka file atau folder", "Pilih dokumen dari penyimpanan perangkat") { openDocumentPicker() })
        body.addView(action("⬇  Unduhan", "Pilih file dari folder Unduhan") { openDocumentPicker("downloads") })
        body.addView(action("🖼  Gambar", "Pilih gambar dari perangkat") { openDocumentPicker("image") })
        body.addView(action("🎵  Audio", "Pilih file audio dari perangkat") { openDocumentPicker("audio") })
        body.addView(action("🎬  Video", "Pilih video dari perangkat") { openDocumentPicker("video") })
        body.addView(label("Catatan: Android meminta izin akses lewat pemilih file; aplikasi tidak memindai seluruh penyimpanan secara diam-diam.", 11, 0xFF9FAFC2.toInt(), false).apply { setPadding(0, dp(12), 0, 0) })
        return ScrollView(this).apply { addView(body) }
    }

    private fun openDocumentPicker(kind: String = "any") {
        val mime = when (kind) { "image" -> "image/*"; "audio" -> "audio/*"; "video" -> "video/*"; else -> "*/*" }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = mime }
        runCatching { startActivity(intent) }.onFailure { message("File Explorer", "Pemilih file Android tidak tersedia.") }
    }

    private fun browserWindow(): View {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(9), dp(8), dp(9), dp(8)) }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val address = EditText(this).apply {
            hint = "Cari atau masukkan alamat web"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(Color.WHITE); setHintTextColor(0xFFADBBCD.toInt())
            background = rounded(0xFF334258.toInt(), 10)
            setPadding(dp(11), dp(4), dp(11), dp(4))
        }
        val go = label("Buka", 12, Color.WHITE, true).apply { gravity = Gravity.CENTER; background = rounded(accent, 9) }
        nav.addView(address, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(0, 0, dp(7), 0) })
        nav.addView(go, LinearLayout.LayoutParams(dp(55), dp(40)))
        body.addView(nav)
        val web = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            loadUrl("https://www.google.com")
        }
        body.addView(web, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        fun navigate() {
            var text = address.text.toString().trim()
            if (text.isBlank()) return
            if (!text.contains('.') || text.contains(' ')) text = "https://www.google.com/search?q=" + Uri.encode(text)
            else if (!text.startsWith("http://") && !text.startsWith("https://")) text = "https://$text"
            web.loadUrl(text)
        }
        go.setOnClickListener { navigate() }
        address.setOnEditorActionListener { _, _, _ -> navigate(); true }
        return body
    }

    private fun notepadWindow(): View {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(label("Disimpan otomatis di perangkat", 11, 0xFFB7C5D7.toInt(), false), LinearLayout.LayoutParams(0, dp(32), 1f))
        lateinit var note: EditText
        val clear = label("Hapus", 12, Color.WHITE, true).apply { gravity = Gravity.CENTER; setOnClickListener { prefs.edit().remove("note").apply(); note.setText("") } }
        actions.addView(clear, LinearLayout.LayoutParams(dp(54), dp(32)))
        container.addView(actions)
        note = EditText(this).apply {
            setText(prefs.getString("note", ""))
            hint = "Mulai mengetik…"
            gravity = Gravity.TOP or Gravity.START
            setTextColor(Color.WHITE); setHintTextColor(0xFFB7C5D7.toInt())
            textSize = 15f
            background = rounded(0xFF1B283A.toInt(), 10)
            setPadding(dp(12), dp(11), dp(12), dp(11))
        }
        container.addView(note, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(6) })
        note.addTextChangedListener(TextWatcher { prefs.edit().putString("note", it).apply() })
        return container
    }

    private fun terminalWindow(): View {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(9), dp(8), dp(9), dp(8)) }
        val output = TextView(this).apply {
            text = "HyperDesk Shell [Android]\nKetik help untuk melihat perintah.\n\nC:\\Users\\Android>"
            setTextColor(0xFFB9F6CA.toInt()); textSize = 12f; typeface = Typeface.MONOSPACE
            setPadding(dp(11), dp(10), dp(11), dp(10)); background = rounded(Color.rgb(8, 13, 19), 10)
        }
        body.addView(output, LinearLayout.LayoutParams(-1, 0, 1f))
        val input = EditText(this).apply {
            hint = "Ketik perintah"
            setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(0xFFACB9C9.toInt())
            background = rounded(0xFF334258.toInt(), 10); setPadding(dp(11), dp(6), dp(11), dp(6))
        }
        body.addView(input, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(7) })
        input.setOnEditorActionListener { _, _, _ ->
            val command = input.text.toString().trim()
            if (command.isNotEmpty()) {
                val result = runCommand(command)
                output.text = if (command.equals("clear", true)) "" else "${output.text}\n$command\n$result\n\nC:\\Users\\Android>"
                input.text.clear()
            }
            true
        }
        return body
    }

    private fun runCommand(raw: String): String {
        val parts = raw.split(Regex("\\s+"), limit = 2)
        return when (parts[0].lowercase()) {
            "help" -> "help | date | apps | battery | storage | open <nama> | clear | about"
            "date" -> DateFormat.getDateTimeInstance().format(Date())
            "apps" -> installedApps.take(35).joinToString("\n") { it.label } + if (installedApps.size > 35) "\n…" else ""
            "battery" -> {
                val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
                "Baterai: ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%"
            }
            "storage" -> {
                val stat = StatFs(Environment.getDataDirectory().path)
                val total = stat.totalBytes / (1024 * 1024 * 1024)
                val free = stat.availableBytes / (1024 * 1024 * 1024)
                "Internal: ${total - free} GB terpakai dari $total GB ($free GB tersedia)"
            }
            "open" -> {
                val name = parts.getOrNull(1).orEmpty()
                val app = installedApps.firstOrNull { it.label.contains(name, true) }
                if (app == null) "Aplikasi '$name' tidak ditemukan." else { runCatching { startActivity(app.launchIntent) }; "Membuka ${app.label}…" }
            }
            "clear" -> ""
            "about" -> "HyperDesk Shell 0.2 — perintah internal launcher, bukan shell Linux umum."
            else -> "Perintah tidak dikenal: ${parts[0]}. Ketik help."
        }
    }

    private fun taskManagerWindow(): View {
        val body = verticalContent()
        body.addView(label("Performa perangkat", 17, Color.WHITE, true))
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memory)
        val used = (memory.totalMem - memory.availMem) / (1024 * 1024)
        val total = memory.totalMem / (1024 * 1024)
        body.addView(metric("Memori", "$used MB dari $total MB"))
        val battery = (getSystemService(BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        body.addView(metric("Baterai", "$battery%"))
        val stat = StatFs(Environment.getDataDirectory().path)
        val free = stat.availableBytes / (1024 * 1024 * 1024)
        val all = stat.totalBytes / (1024 * 1024 * 1024)
        body.addView(metric("Penyimpanan", "${all - free} GB terpakai · $free GB tersedia"))
        body.addView(label("Jendela HyperDesk", 15, Color.WHITE, true).apply { setPadding(0, dp(13), 0, dp(7)) })
        if (openWindows.isEmpty()) body.addView(label("Tidak ada jendela aktif", 12, 0xFFBAC8D8.toInt(), false))
        openWindows.values.toList().forEachIndexed { index, view ->
            val title = (view.getChildAt(0) as? LinearLayout)?.getChildAt(0) as? TextView
            body.addView(action(title?.text?.toString() ?: "Aplikasi ${index + 1}", "Klik untuk menampilkan jendela") { view.visibility = View.VISIBLE; view.bringToFront(); taskbar.bringToFront() })
        }
        body.addView(label("Catatan Android: penggunaan CPU dan penghentian proses aplikasi lain tidak dibuka untuk launcher biasa.", 11, 0xFF9FAFC2.toInt(), false).apply { setPadding(0, dp(12), 0, 0) })
        return ScrollView(this).apply { addView(body) }
    }

    private fun settingsWindow(): View {
        val body = verticalContent()
        body.addView(label("Pengaturan HyperDesk", 18, Color.WHITE, true))
        body.addView(action("Personalisasi", "Ganti tema desktop") { cycleWallpaper() })
        body.addView(action("Ukuran tampilan", "Sesuaikan skala dengan layar") { message("Ukuran tampilan", "Gunakan pengaturan ukuran tampilan Android agar launcher mengikuti skala sistem.") })
        body.addView(action("Aplikasi default", "Atur HyperDesk sebagai launcher Home") {
            runCatching { startActivity(Intent("android.settings.HOME_SETTINGS")) }.onFailure { message("Aplikasi default", "Pilih HyperDesk dari pengaturan aplikasi Home Android.") }
        })
        body.addView(action("Tentang", "HyperDesk 0.2") { message("Tentang HyperDesk", "Launcher desktop Android independen, terinspirasi dari pengalaman desktop HyperDroid.") })
        return ScrollView(this).apply { addView(body) }
    }

    private fun cycleWallpaper() {
        val next = (prefs.getInt("wallpaper", 0) + 1) % 3
        prefs.edit().putInt("wallpaper", next).apply()
        val colors = when (next) {
            1 -> intArrayOf(Color.rgb(16, 56, 64), Color.rgb(27, 110, 112), Color.rgb(9, 29, 42))
            2 -> intArrayOf(Color.rgb(48, 37, 75), Color.rgb(84, 74, 145), Color.rgb(20, 23, 51))
            else -> intArrayOf(backgroundTop, Color.rgb(23, 80, 125), backgroundBottom)
        }
        desktop.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
        message("Personalisasi", "Wallpaper desktop diperbarui.")
    }

    private fun appDrawerWindow(filter: String = "") {
        refreshApps()
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(9), dp(8), dp(9), dp(8)) }
        val search = EditText(this).apply {
            hint = "Cari aplikasi"
            setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(0xFFADBBCD.toInt())
            background = rounded(0xFF334258.toInt(), 10); setPadding(dp(11), dp(5), dp(11), dp(5)); setText(filter)
        }
        body.addView(search, LinearLayout.LayoutParams(-1, dp(40)).apply { bottomMargin = dp(7) })
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(rows) }
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        fun populate(query: String) {
            rows.removeAllViews()
            installedApps.filter { it.label.contains(query, true) }.take(150).forEach { app ->
                rows.addView(action("▣  ${app.label}", app.packageName) {
                    runCatching { startActivity(app.launchIntent) }.onFailure { message("Aplikasi", "Tidak dapat membuka ${app.label}.") }
                })
            }
        }
        populate(filter)
        search.addTextChangedListener(TextWatcher { populate(it) })
        openWindow("Semua aplikasi", { body }, .88, .82, "app-drawer")
    }

    private fun showPowerMenu() {
        AlertDialog.Builder(this).setTitle("Sesi Android").setItems(arrayOf("Pengaturan Android", "Kunci perangkat")) { _, which ->
            if (which == 0) runCatching { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
            else message("Kunci perangkat", "Penguncian perangkat dikendalikan oleh Android dan izin administrator.")
        }.show()
    }

    private fun verticalContent() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(15), dp(14), dp(15), dp(14)) }

    private fun action(title: String, subtitle: String, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(11), dp(9), dp(11), dp(9))
        background = rounded(panelRaised, 11)
        addView(label(title, 13, Color.WHITE, true))
        addView(label(subtitle, 11, 0xFFB9C7D8.toInt(), false).apply { setPadding(0, dp(3), 0, 0) })
        setOnClickListener { onClick() }
        (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin = dp(7)
    }

    private fun metric(name: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(11), dp(10), dp(11), dp(10)); background = rounded(panelRaised, 10)
        addView(label(name, 12, 0xFFCBD7E6.toInt(), false), LinearLayout.LayoutParams(0, -2, 1f))
        addView(label(value, 12, Color.WHITE, true))
        (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin = dp(6)
    }

    private fun windowControl(text: String) = label(text, 15, Color.WHITE, false).apply {
        gravity = Gravity.CENTER
        setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) view.setBackgroundColor(0xFF384A63.toInt())
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) view.setBackgroundColor(Color.TRANSPARENT)
            false
        }
    }

    private fun panelWidth(): Int = min(dp(540), resources.displayMetrics.widthPixels - dp(22))
    private fun panelHeight(): Int = min(dp(520), resources.displayMetrics.heightPixels - dp(82))

    private fun label(textValue: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = textValue; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun message(title: String, text: String) { AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("OK", null).show() }
}

private class TextWatcher(private val onText: (String) -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = onText(s?.toString().orEmpty())
    override fun afterTextChanged(s: android.text.Editable?) = Unit
}

private val R.id.window_maximized: Int get() = 0x7f0a0001
private val R.id.window_saved_bounds: Int get() = 0x7f0a0002
