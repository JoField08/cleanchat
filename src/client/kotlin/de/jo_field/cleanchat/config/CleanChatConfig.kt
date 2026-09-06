package de.jo_field.cleanchat.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

private data class ConfigData(
    var modEnabled: Boolean = true,
    var rules: MutableList<FilterRule> = defaultRules()
)

private fun defaultRules(): MutableList<FilterRule> = mutableListOf(
    FilterRule(
        pattern = "([A-Za-z0-9_]{3,16}) was teleported to ([A-Za-z0-9_]{3,16})",
        mode = FilterMode.REGEX,
        action = FilterAction.HIDE
    )
)

object CleanChatConfig {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val configPath: Path by lazy {
        FabricLoader.getInstance().configDir.resolve("cleanchat.json")
    }

    var modEnabled: Boolean = true
    var rules: MutableList<FilterRule> = defaultRules()
        private set

    fun load() {
        try {
            if (Files.exists(configPath)) {
                Files.newBufferedReader(configPath).use { reader ->
                    val data = gson.fromJson(reader, ConfigData::class.java)
                    if (data != null) {
                        modEnabled = data.modEnabled
                        rules = data.rules ?: defaultRules()
                        return
                    }
                }
            }
        } catch (ex: Exception) {
            // Corrupt/unreadable config: fall back to defaults rather than crashing the game.
            System.err.println("[CleanChat] Failed to read cleanchat.json, using defaults: ${ex.message}")
        }
        // No config yet, or it failed to parse: start from (and immediately write) defaults.
        modEnabled = true
        rules = defaultRules()
        save()
    }

    fun save() {
        try {
            Files.createDirectories(configPath.parent)
            Files.newBufferedWriter(configPath).use { writer ->
                gson.toJson(ConfigData(modEnabled, rules), writer)
            }
        } catch (ex: IOException) {
            System.err.println("[CleanChat] Failed to save cleanchat.json: ${ex.message}")
        }
    }

    fun addRule(rule: FilterRule = FilterRule()) {
        rules.add(rule)
    }

    fun removeRule(rule: FilterRule) {
        rules.remove(rule)
    }
}
