package com.github.sahyuya.simpleDialog.data

import com.github.sahyuya.simpleDialog.SimpleDialog
import com.google.gson.JsonParser
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

// DynamicProfileのJSONからプレイ時間を読み取るクラス
class DynamicProfileReader(private val plugin: SimpleDialog) {

    private val dynamicProfileDir = File(plugin.dataFolder.parentFile, "DynamicProfile/UserStatsJSON")

    // プレイヤーのプレイ時間（分）を取得する。ファイルが存在しない場合はnullを返す
    fun getPlaytime(uuid: UUID): Long? {
        // ファイル名はUUID文字列（拡張子なし）
        val playerFile = File(dynamicProfileDir, uuid.toString())
        if (!playerFile.exists() || !playerFile.isFile) {
            return null
        }

        return try {
            val jsonContent = playerFile.readText()
            val jsonObject = JsonParser.parseString(jsonContent).asJsonObject
            // DynamicProfileのplayTimeは分単位
            jsonObject.get("playTime")?.asLong
        } catch (e: Exception) {
            plugin.logger.warning("DynamicProfileデータの読み込みに失敗しました ($uuid): ${e.message}")
            null
        }
    }

    // 初回参加プレイヤーかどうかを判定する（playTime=0またはファイルなし）
    fun isNewPlayer(uuid: UUID): Boolean {
        val playtime = getPlaytime(uuid)
        return playtime == null || playtime == 0L
    }
}

// プレイヤーの目的・ジャンルデータを管理するクラス
class PlayerDataManager(private val plugin: SimpleDialog) {

    private val dataFile = File(plugin.dataFolder, "playerdata.yml")

    // メモリ上のプレイヤーデータ
    private val playerData = ConcurrentHashMap<UUID, PlayerData>()

    val dynamicProfileReader = DynamicProfileReader(plugin)

    // データをYAMLファイルから読み込む
    fun loadData() {
        if (!dataFile.exists()) {
            dataFile.parentFile.mkdirs()
            dataFile.createNewFile()
            return
        }

        val config = YamlConfiguration.loadConfiguration(dataFile)

        playerData.clear()
        for (key in config.getKeys(false)) {
            val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
            val section = config.getConfigurationSection(key) ?: continue

            val purpose = section.getString("purpose")
            val genres = section.getStringList("genres")

            playerData[uuid] = PlayerData(purpose, genres.toMutableList())
        }

        plugin.logger.info("プレイヤーデータを読み込みました（${playerData.size}件）")
    }

    // データをYAMLファイルに保存する
    fun saveData() {
        val config = YamlConfiguration()

        for ((uuid, data) in playerData) {
            val section = config.createSection(uuid.toString())
            data.purpose?.let { section.set("purpose", it) }
            if (data.genres.isNotEmpty()) {
                section.set("genres", data.genres)
            }
        }

        config.save(dataFile)
    }

    fun getPlayerData(uuid: UUID): PlayerData {
        return playerData.computeIfAbsent(uuid) {
            PlayerData(null, mutableListOf())
        }
    }

    // データが存在する場合のみ返す（存在しなければnull）
    fun getPlayerDataOrNull(uuid: UUID): PlayerData? {
        return playerData[uuid]
    }

    fun removePlayerData(uuid: UUID) {
        playerData.remove(uuid)
    }

    fun hasPlayerData(uuid: UUID): Boolean {
        return playerData.containsKey(uuid)
    }

    fun setPurpose(uuid: UUID, purpose: String) {
        getPlayerData(uuid).purpose = purpose
    }

    fun setGenres(uuid: UUID, genres: List<String>) {
        val data = getPlayerData(uuid)
        data.genres.clear()
        data.genres.addAll(genres)
    }

    fun getGenres(uuid: UUID): List<String>? {
        return playerData[uuid]?.genres
    }

    // タグを完全消去し、データも削除する
    fun clearTags(uuid: UUID) {
        playerData.remove(uuid)
        saveData()
    }

    // DynamicProfileからプレイ時間（分）を取得する。取得できない場合は0を返す
    fun getPlaytime(uuid: UUID): Long {
        return dynamicProfileReader.getPlaytime(uuid) ?: 0L
    }

    // 初回参加かどうか（DynamicProfile未記録 または playTime=0、かつplayerdata未登録）
    fun isNewPlayer(uuid: UUID): Boolean {
        return dynamicProfileReader.isNewPlayer(uuid) && !hasPlayerData(uuid)
    }

    // Dialogを表示すべきかどうかを判定する
    fun shouldShowDialog(uuid: UUID): Boolean {
        // 新規プレイヤーは常に表示
        if (isNewPlayer(uuid)) return true

        // maxPlaytime以内のプレイヤーのみ表示
        val playtime = getPlaytime(uuid)
        return playtime <= plugin.configManager.maxPlaytime
    }

    // maxPlaytimeを超えているプレイヤーのデータを一括削除する
    fun cleanupExpiredPlayers() {
        val maxPlaytime = plugin.configManager.maxPlaytime
        val toRemove = playerData.keys.filter { uuid ->
            dynamicProfileReader.getPlaytime(uuid)?.let { it > maxPlaytime } ?: false
        }

        if (toRemove.isEmpty()) return

        toRemove.forEach { uuid ->
            playerData.remove(uuid)
            plugin.logger.info("期限切れプレイヤーデータを削除しました: $uuid")
        }

        saveData()
    }
}

// プレイヤーごとの目的・ジャンルデータ
data class PlayerData(
    var purpose: String?,
    val genres: MutableList<String>
)