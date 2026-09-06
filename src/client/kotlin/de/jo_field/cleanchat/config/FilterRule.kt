package de.jo_field.cleanchat.config

import java.util.*

data class FilterRule(
    val id: String = UUID.randomUUID().toString(),
    var enabled: Boolean = true,
    var pattern: String = "",
    var mode: FilterMode = FilterMode.STRING,
    var action: FilterAction = FilterAction.HIDE,
)
