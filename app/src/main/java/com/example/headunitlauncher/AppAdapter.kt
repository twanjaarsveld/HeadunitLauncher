package com.example.headunitlauncher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(private val apps: List<ResolveInfo>, private val prefs: SharedPreferences) :
    RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val name: TextView = view.findViewById(R.id.app_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        val pkg = app.activityInfo.packageName
        val pm = holder.itemView.context.packageManager

        // 1. Set the Label
        holder.name.text = app.loadLabel(pm)

        // 2. Custom Icon Logic
        // We look for the key "custom_icon_com.package.name"
        val customIconUriString = prefs.getString("custom_icon_$pkg", null)

        if (customIconUriString != null) {
            try {
                // Clear any existing drawable to prevent caching glitches
                holder.icon.setImageDrawable(null)
                holder.icon.setImageURI(Uri.parse(customIconUriString))
            } catch (e: Exception) {
                // If the URI is invalid or permission was lost, fall back to system icon
                holder.icon.setImageDrawable(app.loadIcon(pm))
            }
        } else {
            // No custom icon saved, use default
            holder.icon.setImageDrawable(app.loadIcon(pm))
        }

        // 3. Launch Intent
        holder.itemView.setOnClickListener {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                holder.itemView.context.startActivity(intent)
            }
        }
    }

    override fun getItemCount() = apps.size
}