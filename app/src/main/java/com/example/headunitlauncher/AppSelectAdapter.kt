package com.example.headunitlauncher

import android.content.Context
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppSelectAdapter(
    private val allApps: List<ResolveInfo>,
    private val selectedApps: MutableSet<String>,
    private val onAppLongClick: (String) -> Unit
) : RecyclerView.Adapter<AppSelectAdapter.ViewHolder>() {

    private val handler = Handler(Looper.getMainLooper())
    private var longClickRunnable: Runnable? = null
    private var isLongPressTriggered = false

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.item_app_icon)
        val name: TextView = view.findViewById(R.id.item_app_name)
        val checkBox: CheckBox = view.findViewById(R.id.item_app_checkbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_select, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = allApps[position]
        val pkg = app.activityInfo.packageName
        val context = holder.itemView.context
        val pm = context.packageManager
        val prefs = context.getSharedPreferences("TeslaLauncher", Context.MODE_PRIVATE)

        holder.name.text = app.loadLabel(pm)

        val customIconUri = prefs.getString("custom_icon_$pkg", null)
        if (customIconUri != null) {
            try {
                holder.icon.setImageURI(null)
                holder.icon.setImageURI(Uri.parse(customIconUri))
            } catch (e: Exception) {
                holder.icon.setImageDrawable(app.loadIcon(pm))
            }
        } else {
            holder.icon.setImageDrawable(app.loadIcon(pm))
        }

        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = selectedApps.contains(pkg)

        // TOTAL MANUAL CONTROL - Fixes the Top-Left bug AND allows Long Click
        holder.itemView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isLongPressTriggered = false
                    view.setBackgroundColor(Color.parseColor("#333333"))

                    // Start a timer for 600ms. If the finger stays down, trigger long click.
                    longClickRunnable = Runnable {
                        isLongPressTriggered = true
                        onAppLongClick(pkg)
                    }
                    handler.postDelayed(longClickRunnable!!, 600)
                }

                MotionEvent.ACTION_UP -> {
                    view.setBackgroundColor(Color.BLACK)
                    handler.removeCallbacks(longClickRunnable!!) // Cancel the long click timer

                    // Only toggle the checkbox if we didn't just trigger a long press
                    if (!isLongPressTriggered) {
                        val isNowChecked = !selectedApps.contains(pkg)
                        if (isNowChecked) selectedApps.add(pkg) else selectedApps.remove(pkg)
                        holder.checkBox.isChecked = isNowChecked
                        prefs.edit().putStringSet("allowed_apps", selectedApps).apply()
                        view.performClick()
                    }
                }

                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_MOVE -> {
                    // If the user moves their finger significantly or cancels, stop the timer
                    if (event.action == MotionEvent.ACTION_CANCEL ||
                        (event.action == MotionEvent.ACTION_MOVE && isOutsideView(view, event))) {
                        view.setBackgroundColor(Color.BLACK)
                        handler.removeCallbacks(longClickRunnable!!)
                    }
                }
            }
            true // Returning TRUE fixes the Top-Left drift bug permanently
        }
    }

    // Helper to check if finger moved outside the row
    private fun isOutsideView(v: View, e: MotionEvent): Boolean {
        return e.x < 0 || e.x > v.width || e.y < 0 || e.y > v.height
    }

    override fun getItemCount() = allApps.size
    fun getSelectedApps(): Set<String> = selectedApps
}