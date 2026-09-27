package com.jarvis.clock.alarm

import android.content.Context
import com.jarvis.clock.Events
import org.json.JSONArray

/** Persistencia de alarmas en SharedPreferences (JSON). Pocas decenas como mucho. */
object AlarmStore {
    private const val FILE = "alarms"
    private const val KEY = "list"
    private const val KEY_NEXT_ID = "next_id"

    @Synchronized
    fun all(ctx: Context): List<Alarm> {
        val raw = sp(ctx).getString(KEY, "[]")
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { runCatching { Alarm.fromStorage(arr.getJSONObject(it)) }.getOrNull() }
            .sortedWith(compareBy({ it.isAbsolute }, { it.hour }, { it.minute }))
    }

    @Synchronized
    fun get(ctx: Context, id: Int): Alarm? = all(ctx).firstOrNull { it.id == id }

    @Synchronized
    fun newId(ctx: Context): Int {
        val p = sp(ctx)
        val id = p.getInt(KEY_NEXT_ID, 1)
        p.edit().putInt(KEY_NEXT_ID, id + 1).apply()
        return id
    }

    @Synchronized
    fun upsert(ctx: Context, alarm: Alarm) {
        save(ctx, all(ctx).filter { it.id != alarm.id } + alarm)
    }

    @Synchronized
    fun remove(ctx: Context, id: Int): Boolean {
        val list = all(ctx)
        if (list.none { it.id == id }) return false
        save(ctx, list.filter { it.id != id })
        return true
    }

    private fun save(ctx: Context, list: List<Alarm>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toStorage()) }
        sp(ctx).edit().putString(KEY, arr.toString()).commit()
        Events.post(Events.Event.AlarmsChanged)
    }

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
