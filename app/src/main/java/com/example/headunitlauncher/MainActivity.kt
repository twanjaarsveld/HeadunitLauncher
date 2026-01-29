package com.example.headunitlauncher

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.location.*
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.*
import android.text.style.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.view.*
import androidx.recyclerview.widget.*

class MainActivity : AppCompatActivity(), LocationListener {

    private var audioManager: AudioManager? = null
    private var locationManager: LocationManager? = null
    private var isShowingSpeed = false
    private var isMusicPlaying = false
    private var lastUserTapTime: Long = 0
    private val BLOCK_SYSTEM_UPDATES_MS = 1500L

    // --- NEW: THEME & SCALE ENGINE ---
    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("TeslaLauncher", Context.MODE_PRIVATE)
        val scalePercent = prefs.getInt("ui_scale", 100)
        val isDarkMode = prefs.getBoolean("is_dark_mode", true)

        val config = Configuration(newBase.resources.configuration)

        // Apply DPI Scaling
        config.densityDpi = (newBase.resources.displayMetrics.densityDpi * (scalePercent / 100f)).toInt()

        // Apply Dark/Light Mode Configuration
        config.uiMode = if (isDarkMode) {
            (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        } else {
            (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        }

        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    private val musicReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.headunitlauncher.MUSIC_STATE_CHANGED") {
                val systemReportedPlaying = intent.getBooleanExtra("isPlaying", false)
                if (System.currentTimeMillis() - lastUserTapTime > BLOCK_SYSTEM_UPDATES_MS) {
                    isMusicPlaying = systemReportedPlaying
                    updatePlayPauseIcon()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Force theme before super.onCreate
        val prefs = getSharedPreferences("TeslaLauncher", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("is_dark_mode", true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val splashOverlay = findViewById<View>(R.id.splash_overlay)
        val mainContent = findViewById<View>(R.id.main_content)

        initializeUI()

        // Handle the custom splash text fade-out
        Handler(Looper.getMainLooper()).postDelayed({
            mainContent?.animate()?.alpha(1f)?.setDuration(600)?.start()
            splashOverlay?.animate()?.alpha(0f)?.setDuration(600)?.withEndAction {
                splashOverlay.visibility = View.GONE
            }?.start()
        }, 2000)
    }

    private fun initializeUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Background will now follow ?attr/mainBgColor from XML automatically

        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        val filter = IntentFilter("com.example.headunitlauncher.MUSIC_STATE_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(musicReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(musicReceiver, filter)
        }

        fullUIRefresh()
        checkLocationPermissions()
        checkNotificationPermission()
    }

    private fun fullUIRefresh() {
        hideSystemUI()
        setupMediaControls()
        setupVolumeSlider()
        setupSwipeDetection()
        setupDevLinkSwipe()

        findViewById<ImageView>(R.id.car_visual)?.setOnLongClickListener {
            try { startActivity(Intent(this, SettingsActivity::class.java)) }
            catch (e: Exception) { Toast.makeText(this, "Settings Not Found", Toast.LENGTH_SHORT).show() }
            true
        }

        refreshUI()
        updatePlayPauseIcon()
        setStyledSpeed(0)
    }

    private fun setupMediaControls() {
        findViewById<View>(R.id.btn_play_pause)?.setOnClickListener {
            lastUserTapTime = System.currentTimeMillis()
            isMusicPlaying = !isMusicPlaying
            updatePlayPauseIcon()
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
        findViewById<View>(R.id.btn_next)?.setOnClickListener { sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
        findViewById<View>(R.id.btn_prev)?.setOnClickListener { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
    }

    private fun sendMediaKey(code: Int) {
        try {
            val mm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = mm.getActiveSessions(ComponentName(this, MusicService::class.java))
            val eventTime = System.currentTimeMillis()

            if (sessions.isNotEmpty()) {
                val controller = sessions[0]
                controller.dispatchMediaButtonEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, code, 0))
                controller.dispatchMediaButtonEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, code, 0))
            } else {
                audioManager?.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, code, 0))
                audioManager?.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, code, 0))
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun setStyledSpeed(speed: Int) {
        val speedTextView = findViewById<TextView>(R.id.speed_text) ?: return
        val speedStr = speed.toString()
        val fullText = "$speedStr Km/u"
        val spannable = SpannableString(fullText)

        // Speed number
        spannable.setSpan(RelativeSizeSpan(2.5f), 0, speedStr.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // "Km/u" label
        spannable.setSpan(RelativeSizeSpan(0.6f), speedStr.length, fullText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Use a semi-transparent version of the primary text color instead of hardcoded gray
        val labelColor = if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)
            Color.parseColor("#88FFFFFF") else Color.parseColor("#88000000")

        spannable.setSpan(ForegroundColorSpan(labelColor), speedStr.length, fullText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        speedTextView.text = spannable
    }

    private fun setupDevLinkSwipe() {
        val devText = findViewById<TextView>(R.id.dev_text_link) ?: return
        var dY = 0f
        devText.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { dY = view.translationY - event.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    val newY = event.rawY + dY
                    if (newY > 0) view.translationY = newY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (view.translationY > 40) {
                        view.animate().translationY(200f).alpha(0f).setDuration(300).withEndAction { view.visibility = View.GONE }.start()
                    } else { view.animate().translationY(0f).setDuration(200).start() }
                    true
                }
                else -> false
            }
        }
    }

    private fun hideSystemUI() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun refreshUI() {
        val prefs = getSharedPreferences("TeslaLauncher", Context.MODE_PRIVATE)
        val switcher = findViewById<ViewSwitcher>(R.id.left_panel_switcher)

        isShowingSpeed = prefs.getBoolean("startup_view_speedo", false)
        if (isShowingSpeed && switcher?.displayedChild == 0) switcher.showNext()
        else if (!isShowingSpeed && switcher?.displayedChild == 1) switcher.showPrevious()

        findViewById<ImageView>(R.id.car_visual)?.let { v ->
            val uriStr = prefs.getString("car_image_uri", null)
            if (!uriStr.isNullOrEmpty()) v.setImageURI(Uri.parse(uriStr))
            else v.setImageResource(R.drawable.tesla_model_3)
        }

        val rv = findViewById<RecyclerView>(R.id.apps_list) ?: return
        rv.layoutManager = GridLayoutManager(this, prefs.getInt("app_grid_count", 4))

        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val saved = prefs.getStringSet("allowed_apps", emptySet()) ?: emptySet()
        val filtered = packageManager.queryIntentActivities(intent, 0).filter { saved.isEmpty() || saved.contains(it.activityInfo.packageName) }
        rv.adapter = AppAdapter(filtered, prefs)
    }

    private fun setupVolumeSlider() {
        val slider = findViewById<SeekBar>(R.id.volume_slider) ?: return
        audioManager?.let { am ->
            slider.max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            slider.progress = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { if (f) am.setStreamVolume(AudioManager.STREAM_MUSIC, p, 0) }
                override fun onStartTrackingTouch(p0: SeekBar?) {}
                override fun onStopTrackingTouch(p0: SeekBar?) {}
            })
        }
    }

    private fun setupSwipeDetection() {
        val leftPanel = findViewById<View>(R.id.left_panel) ?: return
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
                isShowingSpeed = !isShowingSpeed
                val switcher = findViewById<ViewSwitcher>(R.id.left_panel_switcher)
                if (isShowingSpeed) switcher?.showNext() else switcher?.showPrevious()
                return true
            }
        })
        leftPanel.setOnTouchListener { _, e -> gd.onTouchEvent(e); true }
    }

    private fun updatePlayPauseIcon() {
        runOnUiThread {
            val btn = findViewById<ImageView>(R.id.btn_play_pause) ?: return@runOnUiThread
            btn.setImageResource(if (isMusicPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        }
    }

    private fun checkNotificationPermission() {
        val listeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (listeners == null || !listeners.contains(packageName)) {
            try { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) } catch (e: Exception) {}
        }
    }

    private fun checkLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
        } else { startLocationUpdates() }
    }

    private fun startLocationUpdates() {
        try { locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this) } catch (e: Exception) {}
    }

    override fun onLocationChanged(l: Location) { runOnUiThread { setStyledSpeed((l.speed * 3.6).toInt()) } }
    override fun onResume() {
        super.onResume()
        hideSystemUI()
        // We don't call recreate() here because the Save & Exit button in Settings
        // already restarts the whole process, which is cleaner.
        refreshUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(musicReceiver) } catch (e: Exception) {}
        locationManager?.removeUpdates(this)
    }

    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
    override fun onProviderEnabled(p: String) {}
    override fun onProviderDisabled(p: String) {}
    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, gr: IntArray) {
        if (rc == 101 && gr.isNotEmpty() && gr[0] == PackageManager.PERMISSION_GRANTED) startLocationUpdates()
    }
}