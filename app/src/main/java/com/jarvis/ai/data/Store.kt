package com.jarvis.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ChatMsg(val role: String, val text: String, val time: Long = System.currentTimeMillis())

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("apiKey", "") ?: ""
        set(v) { sp.edit().putString("apiKey", v.trim()).apply() }

    var model: String
        get() = sp.getString("model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
        set(v) { sp.edit().putString("model", v).apply() }

    var bossOn: Boolean
        get() = sp.getBoolean("boss", true)
        set(v) { sp.edit().putBoolean("boss", v).apply() }

    var bgOn: Boolean
        get() = sp.getBoolean("bg", false)
        set(v) { sp.edit().putBoolean("bg", v).apply() }

    var femaleVoice: Boolean
        get() = sp.getBoolean("female", true)
        set(v) { sp.edit().putBoolean("female", v).apply() }

    var timeoutSec: Int
        get() = sp.getInt("timeout", 20)
        set(v) { sp.edit().putInt("timeout", v.coerceIn(5, 300)).apply() }

    fun chat(): MutableList<ChatMsg> {
        val out = mutableListOf<ChatMsg>()
        try {
            val arr = JSONArray(sp.getString("chat", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(ChatMsg(o.getString("role"), o.getString("text"), o.optLong("time")))
            }
        } catch (_: Exception) { }
        if (out.isEmpty()) out.add(ChatMsg("ai", "Namaste! Main JARVIS hoon - aapka AI assistant. Ask me anything, Hindi ya English."))
        return out
    }

    fun saveChat(list: List<ChatMsg>) {
        val arr = JSONArray()
        list.takeLast(100).forEach { arr.put(JSONObject().put("role", it.role).put("text", it.text).put("time", it.time)) }
        sp.edit().putString("chat", arr.toString()).apply()
    }

    fun tasks(): List<String> {
        val out = mutableListOf<String>()
        try {
            val arr = JSONArray(sp.getString("tasks", "[]") ?: "[]")
            for (i in 0 until arr.length()) out.add(arr.getString(i))
        } catch (_: Exception) { }
        return out
    }

    fun saveTasks(list: List<String>) {
        val arr = JSONArray()
        list.takeLast(50).forEach { arr.put(it) }
        sp.edit().putString("tasks", arr.toString()).apply()
    }
}
