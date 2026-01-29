package com.example.headunitlauncher

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SettingsActivity : AppCompatActivity() {

    private lateinit var adapter: AppSelectAdapter
    private lateinit var systemAdapter: SystemAppAdapter
    private var isPickingWallpaper = true
    private var targetPackageForIcon: String? = null

    private lateinit var sectionApps: LinearLayout
    private lateinit var sectionAllApps: LinearLayout
    private lateinit var sectionInfo: LinearLayout
    private lateinit var sectionOptions: LinearLayout

    private lateinit var btnInfo: Button
    private lateinit var btnApps: Button
    private lateinit var btnAllApps: Button
    private lateinit var btnOptions: Button

    // Track the active tab ID to survive recreation
    private var activeTabId: Int = R.id.settings_btn_info

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("TeslaLauncher", Context.MODE_PRIVATE)
        val scalePercent = prefs.getInt("ui_scale", 100)

        val config = Configuration(newBase.resources.configuration)
        config.densityDpi = (newBase.resources.displayMetrics.densityDpi * (scalePercent / 100f)).toInt()

        val isDarkMode = prefs.getBoolean("is_dark_mode", true)
        config.uiMode = if (isDarkMode) {
            (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        } else {
            (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        }

        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                saveUriPermission(uri)
                val prefs = getSharedPreferences("TeslaLauncher", Context.MODE_PRIVATE)
                val key = if (isPickingWallpaper) "right_wallpaper_uri" else "car_image_uri"
                prefs.edit().putString(key, uri.toString()).apply()
                Toast.makeText(this, "Image Saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val pickCustomIconLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && targetPackageForIcon != null) {
            result.data?.data?.let { uri ->
                saveUriPermission(uri)
                val prefs = getSharedPreferences("TeslaLauncher", Context.MODE_PRIVATE)
                prefs.edit().putString("custom_icon_$targetPackageForIcon", uri.toString()).apply()
                Toast.makeText(this, "Icon updated!", Toast.LENGTH_SHORT).show()
                targetPackageForIcon = null
                adapter.notifyDataSetChanged()
                if (::systemAdapter.isInitialized) systemAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("TeslaLauncher", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("is_dark_mode", true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        hideSystemUI()
        setContentView(R.layout.activity_settings)

        sectionApps = findViewById(R.id.section_apps)
        sectionAllApps = findViewById(R.id.section_all_apps)
        sectionInfo = findViewById(R.id.section_info)
        sectionOptions = findViewById(R.id.section_startup)

        btnInfo = findViewById(R.id.settings_btn_info)
        btnApps = findViewById(R.id.settings_btn_apps)
        btnAllApps = findViewById(R.id.settings_btn_all_apps)
        btnOptions = findViewById(R.id.settings_btn_startup)

        btnInfo.setOnClickListener { selectTab(it as Button, sectionInfo); updateSoftwareInfo() }
        btnApps.setOnClickListener { selectTab(it as Button, sectionApps) }
        btnAllApps.setOnClickListener { selectTab(it as Button, sectionAllApps) }
        btnOptions.setOnClickListener { selectTab(it as Button, sectionOptions) }

        findViewById<Button>(R.id.settings_btn_wallpaper).setOnClickListener {
            isPickingWallpaper = true
            openGallery(pickImageLauncher)
        }
        findViewById<Button>(R.id.settings_btn_car).setOnClickListener {
            isPickingWallpaper = false
            openGallery(pickImageLauncher)
        }

        setupAppSelection(prefs)
        setupOptionsLogic(prefs)

        // Restore tab or default to Info
        if (savedInstanceState == null) {
            btnInfo.performClick()
        }

        findViewById<Button>(R.id.btn_save_settings).setOnClickListener {
            prefs.edit().putStringSet("allowed_apps", adapter.getSelectedApps()).apply()
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            val mainIntent = Intent.makeRestartActivityTask(intent?.component)
            startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("ACTIVE_TAB", activeTabId)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        activeTabId = savedInstanceState.getInt("ACTIVE_TAB", R.id.settings_btn_info)
        findViewById<Button>(activeTabId)?.performClick()
    }

    private fun selectTab(selectedBtn: Button, targetSection: View) {
        activeTabId = selectedBtn.id
        val buttons = listOf(btnInfo, btnApps, btnAllApps, btnOptions)
        buttons.forEach { it.setTextColor(Color.parseColor("#AAAAAA")) }
        selectedBtn.setTextColor(Color.WHITE)

        sectionApps.visibility = View.GONE
        sectionAllApps.visibility = View.GONE
        sectionInfo.visibility = View.GONE
        sectionOptions.visibility = View.GONE
        targetSection.visibility = View.VISIBLE
    }

    private fun setupOptionsLogic(prefs: android.content.SharedPreferences) {
        // Startup View Logic
        val startWithSpeedo = prefs.getBoolean("startup_view_speedo", false)
        findViewById<RadioButton>(if (startWithSpeedo) R.id.radio_start_speedo else R.id.radio_start_clock)?.isChecked = true
        findViewById<RadioGroup>(R.id.radio_group_startup)?.setOnCheckedChangeListener { _, checkedId ->
            prefs.edit().putBoolean("startup_view_speedo", checkedId == R.id.radio_start_speedo).apply()
        }

        // Dark Mode Logic
        val darkModeSwitch = findViewById<SwitchCompat>(R.id.switch_dark_mode)
        darkModeSwitch?.isChecked = prefs.getBoolean("is_dark_mode", true)
        darkModeSwitch?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("is_dark_mode", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            recreate()
        }

        // Display Scaling Logic
        val scaleSeekBar = findViewById<SeekBar>(R.id.settings_scale_seekbar)
        val scaleValueText = findViewById<TextView>(R.id.settings_scale_value)
        val currentScale = prefs.getInt("ui_scale", 100)
        scaleSeekBar?.progress = currentScale - 50
        scaleValueText?.text = "$currentScale%"
        scaleSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                val value = p + 50
                scaleValueText?.text = "$value%"
                prefs.edit().putInt("ui_scale", value).apply()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                recreate()
            }
        })

        // NEW: Brightness Logic
        val brightnessSeekBar = findViewById<SeekBar>(R.id.settings_brightness_seekbar)
        try {
            val curBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            brightnessSeekBar?.progress = curBrightness
        } catch (e: Exception) { e.printStackTrace() }

        brightnessSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (Settings.System.canWrite(this@SettingsActivity)) {
                            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, progress)
                        } else {
                            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                            intent.data = Uri.parse("package:$packageName")
                            startActivity(intent)
                        }
                    } else {
                        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, progress)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupAppSelection(prefs: android.content.SharedPreferences) {
        val gridSeekBar = findViewById<SeekBar>(R.id.settings_grid_seekbar)
        val gridValueText = findViewById<TextView>(R.id.settings_grid_value)
        val savedGrid = prefs.getInt("app_grid_count", 4)
        gridSeekBar.progress = savedGrid - 2
        gridValueText.text = savedGrid.toString()

        gridSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                val value = p + 2
                gridValueText.text = value.toString()
                prefs.edit().putInt("app_grid_count", value).apply()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val allApps = pm.queryIntentActivities(mainIntent, 0).sortedBy { it.loadLabel(pm).toString().lowercase() }
        val savedApps = prefs.getStringSet("allowed_apps", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        val appsList = findViewById<RecyclerView>(R.id.settings_apps_list)
        adapter = AppSelectAdapter(allApps, savedApps) { packageName ->
            targetPackageForIcon = packageName
            openGallery(pickCustomIconLauncher)
        }
        appsList.layoutManager = LinearLayoutManager(this)
        appsList.adapter = adapter

        val allAppsRecyclerView = findViewById<RecyclerView>(R.id.all_system_apps_list)
        systemAdapter = SystemAppAdapter(allApps)
        allAppsRecyclerView.layoutManager = LinearLayoutManager(this)
        allAppsRecyclerView.adapter = systemAdapter
    }

    private fun updateSoftwareInfo() {
        try {
            val actManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val totalRam = Math.round(memInfo.totalMem / 1073741824.0)

            val stat = StatFs(Environment.getDataDirectory().path)
            val totalStorage = (stat.blockCountLong * stat.blockSizeLong) / 1073741824L
            val availStorage = (stat.availableBlocksLong * stat.blockSizeLong) / 1073741824L
            val usedStorage = totalStorage - availStorage

            val currentVersionName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionName
            }

            findViewById<TextView>(R.id.info_device).text = "${Build.MODEL}\nSystem RAM: ${totalRam}GB"
            findViewById<TextView>(R.id.info_storage).text = "Used: ${usedStorage}GB / Total: ${totalStorage}GB"
            findViewById<TextView>(R.id.info_version).text = "Build Version: $currentVersionName\nAndroid OS: ${Build.VERSION.RELEASE}"

        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun openGallery(launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launcher.launch(intent)
    }

    private fun saveUriPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    inner class SystemAppAdapter(private val apps: List<android.content.pm.ResolveInfo>) :
        RecyclerView.Adapter<SystemAppAdapter.ViewHolder>() {

        private val handler = Handler(Looper.getMainLooper())
        private var longClickRunnable: Runnable? = null
        private var isLongPressTriggered = false

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.item_app_icon)
            val name: TextView = view.findViewById(R.id.item_app_name)
            val checkbox: CheckBox = view.findViewById(R.id.item_app_checkbox)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_select, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            val pm = packageManager
            val pkgName = app.activityInfo.packageName

            holder.name.text = app.loadLabel(pm)
            holder.icon.setImageDrawable(app.loadIcon(pm))
            holder.checkbox.visibility = View.GONE

            holder.itemView.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isLongPressTriggered = false
                        view.setBackgroundColor(Color.parseColor("#333333"))
                        longClickRunnable = Runnable {
                            isLongPressTriggered = true
                            targetPackageForIcon = pkgName
                            openGallery(pickCustomIconLauncher)
                        }
                        handler.postDelayed(longClickRunnable!!, 600)
                    }
                    MotionEvent.ACTION_UP -> {
                        view.setBackgroundColor(Color.TRANSPARENT)
                        handler.removeCallbacks(longClickRunnable!!)
                        if (!isLongPressTriggered) {
                            val launchIntent = pm.getLaunchIntentForPackage(pkgName)
                            if (launchIntent != null) startActivity(launchIntent)
                            view.performClick()
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        view.setBackgroundColor(Color.TRANSPARENT)
                        handler.removeCallbacks(longClickRunnable!!)
                    }
                }
                true
            }
        }
        override fun getItemCount() = apps.size
    }
}