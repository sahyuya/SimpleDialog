package com.github.sahyuya.simpleDialog

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID

// 初参加プレイヤーのプレイ時間を記録する専用トラッカー。
class PlaytimeTracker(private val plugin: JavaPlugin) {
    // 初参加プレイヤーのみを対象にプレイ時間を記録する。
    private val dataFile = File(plugin.dataFolder, "playtime.yml")
    private val totalsMillis = mutableMapOf<UUID, Long>()
    private val sessionStarts = mutableMapOf<UUID, Long>()

    private var maxPlaytimeHours = 3.0
    private var dirty = false
    private var lastSaveAt = 0L
    private var saveIntervalMs = 60_000L

    fun load() {
        reloadSettings()
        totalsMillis.clear()
        sessionStarts.clear()
        if (!dataFile.exists()) {
            return
        }
        val config = YamlConfiguration.loadConfiguration(dataFile)
        val section = config.getConfigurationSection("players") ?: return
        for (key in section.getKeys(false)) {
            val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
            val value = config.getLong("players.$key.playtime-ms", 0L)
            totalsMillis[uuid] = value.coerceAtLeast(0L)
        }
        cleanupOverLimit()
    }

    fun reloadSettings() {
        maxPlaytimeHours = plugin.config.getDouble("max-playtime-hours", 3.0)
        val seconds = plugin.config.getLong("tag-check-interval-seconds", 60).coerceAtLeast(5)
        saveIntervalMs = seconds * 1000L
    }

    fun startSession(player: Player, trackIfMissing: Boolean): Boolean {
        val uuid = player.uniqueId
        val exists = totalsMillis.containsKey(uuid)
        if (!exists && !trackIfMissing) {
            return false
        }
        if (exists && isOverLimit(uuid)) {
            untrack(uuid)
            saveIfNeeded(force = true)
            return false
        }
        val isNew = !exists
        if (isNew) {
            totalsMillis[uuid] = 0L
        }
        sessionStarts[uuid] = System.currentTimeMillis()
        if (isNew) {
            dirty = true
            saveIfNeeded(force = true)
        }
        return isNew
    }

    fun stopSession(player: Player) {
        updatePlayer(player.uniqueId)
        sessionStarts.remove(player.uniqueId)
        saveIfNeeded(force = true)
    }

    fun tick() {
        // 定期的に更新してクラッシュ時の損失を最小化する。
        val uuids = sessionStarts.keys.toList()
        for (uuid in uuids) {
            updatePlayer(uuid)
        }
        cleanupOverLimit()
        saveIfNeeded(force = false)
    }

    fun getTotalMillis(uuid: UUID): Long {
        val base = totalsMillis[uuid] ?: 0L
        val lastStart = sessionStarts[uuid] ?: return base
        val delta = (System.currentTimeMillis() - lastStart).coerceAtLeast(0L)
        return base + delta
    }

    fun getTotalHours(player: Player): Double {
        return getTotalMillis(player.uniqueId) / 3_600_000.0
    }

    fun isTracked(uuid: UUID): Boolean {
        return totalsMillis.containsKey(uuid)
    }

    fun isWithinLimit(player: Player): Boolean {
        val uuid = player.uniqueId
        if (!totalsMillis.containsKey(uuid)) {
            return false
        }
        if (maxPlaytimeHours <= 0) {
            return true
        }
        if (isOverLimit(uuid)) {
            untrack(uuid)
            saveIfNeeded(force = true)
            return false
        }
        return true
    }

    private fun cleanupOverLimit() {
        if (maxPlaytimeHours <= 0) {
            return
        }
        val toRemove = mutableListOf<UUID>()
        for (uuid in totalsMillis.keys) {
            if (isOverLimit(uuid)) {
                toRemove.add(uuid)
            }
        }
        if (toRemove.isEmpty()) {
            return
        }
        for (uuid in toRemove) {
            untrack(uuid)
        }
        saveIfNeeded(force = true)
    }

    private fun isOverLimit(uuid: UUID): Boolean {
        if (maxPlaytimeHours <= 0) {
            return false
        }
        val hours = getTotalMillis(uuid) / 3_600_000.0
        return hours > maxPlaytimeHours
    }

    private fun untrack(uuid: UUID) {
        totalsMillis.remove(uuid)
        sessionStarts.remove(uuid)
        dirty = true
    }

    private fun updatePlayer(uuid: UUID) {
        val lastStart = sessionStarts[uuid] ?: return
        val now = System.currentTimeMillis()
        if (now <= lastStart) {
            return
        }
        val total = (totalsMillis[uuid] ?: 0L) + (now - lastStart)
        totalsMillis[uuid] = total
        sessionStarts[uuid] = now
        dirty = true
    }

    private fun saveIfNeeded(force: Boolean) {
        if (!dirty) {
            return
        }
        val now = System.currentTimeMillis()
        if (!force && now - lastSaveAt < saveIntervalMs) {
            return
        }
        save(now)
    }

    private fun save(now: Long) {
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }
        val config = YamlConfiguration()
        for ((uuid, total) in totalsMillis) {
            config.set("players.$uuid.playtime-ms", total)
        }
        runCatching { config.save(dataFile) }
            .onFailure { plugin.logger.warning("playtime.ymlの保存に失敗しました: ${it.message}") }
        lastSaveAt = now
        dirty = false
    }
}
