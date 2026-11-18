package com.pctesting.vidcut.data

import android.content.Context

enum class ThemeMode(val id: String, val label: String) {
    DARK("dark", "Dark"),
    LIGHT("light", "Light"),
    SYSTEM("system", "Match phone");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: DARK
    }
}

class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = ThemeMode.fromId(prefs.getString("theme", null))
        set(value) { prefs.edit().putString("theme", value.id).apply() }
}
