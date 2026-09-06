package de.jo_field.cleanchat

import de.jo_field.cleanchat.config.CleanChatConfig
import de.jo_field.cleanchat.filter.FilterResult
import de.jo_field.cleanchat.filter.MessageFilter
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

class CleanChatClient : ClientModInitializer {

    override fun onInitializeClient() {
        CleanChatConfig.load()

        ClientReceiveMessageEvents.ALLOW_CHAT.register { message, _, _, _, _ ->
            handleIncoming(message, overlay = false)
        }

        ClientReceiveMessageEvents.ALLOW_GAME.register { message, overlay ->
            handleIncoming(message, overlay)
        }
    }

    private fun handleIncoming(message: Component, overlay: Boolean): Boolean {
        if (!CleanChatConfig.modEnabled) return true

        val plainText = message.string
        return when (val result = MessageFilter.evaluate(CleanChatConfig.rules, plainText)) {
            is FilterResult.Pass -> true
            is FilterResult.Hide -> false
            is FilterResult.Censor -> {
                printReplacement(result.censoredText, overlay)
                false
            }
        }
    }

    private fun printReplacement(text: String, overlay: Boolean) {
        val client = Minecraft.getInstance()
        val replacement = Component.literal(text)

        if (overlay) {
            client.gui.hud.setOverlayMessage(replacement, false)
        } else {
            client.gui.hud.chat.addClientSystemMessage(replacement)
        }
    }
}
