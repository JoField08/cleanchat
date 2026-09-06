package de.jo_field.cleanchat.config

enum class FilterAction(val displayName: String) {
    HIDE("Hide entire message"),
    CENSOR_MESSAGE("Censor entire message"),
    CENSOR_WORD("Censor matched word only");

    fun next(): FilterAction = entries[(ordinal + 1) % entries.size]
}
