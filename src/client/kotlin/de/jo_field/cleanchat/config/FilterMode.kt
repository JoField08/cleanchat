package de.jo_field.cleanchat.config

enum class FilterMode(val displayName: String) {
    STRING("Contains String"),
    EQUALS_IGNORE_CASE("Contains UpperLower"),
    REGEX("Regex");

    fun next(): FilterMode = entries[(ordinal + 1) % entries.size]
}
