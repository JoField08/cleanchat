package de.jo_field.cleanchat.filter

import de.jo_field.cleanchat.config.FilterAction
import de.jo_field.cleanchat.config.FilterMode
import de.jo_field.cleanchat.config.FilterRule

sealed class FilterResult {
    data object Pass : FilterResult()
    data class Hide(val rule: FilterRule) : FilterResult()
    data class Censor(val rule: FilterRule, val censoredText: String) : FilterResult()
}

object MessageFilter {

    private const val CENSOR_CHAR = '*'

    fun matches(rule: FilterRule, plainText: String): Boolean {
        if (!rule.enabled || rule.pattern.isEmpty()) return false
        return try {
            when (rule.mode) {
                FilterMode.STRING -> plainText.contains(rule.pattern)
                FilterMode.EQUALS_IGNORE_CASE -> plainText.contains(rule.pattern, ignoreCase = true)
                FilterMode.REGEX -> Regex(rule.pattern).containsMatchIn(plainText)
            }
        } catch (ex: Exception) {
            // Invalid/incomplete regex while the user is still typing it -> just don't match.
            false
        }
    }

    /** Runs [plainText] through [rules] in order and returns the first matching outcome. */
    fun evaluate(rules: List<FilterRule>, plainText: String): FilterResult {
        for (rule in rules) {
            if (matches(rule, plainText)) {
                return when (rule.action) {
                    FilterAction.HIDE -> FilterResult.Hide(rule)
                    FilterAction.CENSOR_MESSAGE -> FilterResult.Censor(rule, censorAll(plainText))
                    FilterAction.CENSOR_WORD -> FilterResult.Censor(rule, censorMatch(rule, plainText))
                }
            }
        }
        return FilterResult.Pass
    }

    private fun censorAll(text: String): String =
        text.map { if (it.isWhitespace()) it else CENSOR_CHAR }.joinToString("")

    private fun censorMatch(rule: FilterRule, text: String): String {
        return try {
            when (rule.mode) {
                FilterMode.REGEX -> {
                    Regex(rule.pattern).replace(text) { match -> mask(match.value) }
                }
                FilterMode.STRING -> {
                    if (rule.pattern.isEmpty()) return text
                    buildString {
                        var index = 0
                        while (index < text.length) {
                            val found = text.indexOf(rule.pattern, index)
                            if (found < 0) {
                                append(text, index, text.length)
                                break
                            }
                            append(text, index, found)
                            append(mask(rule.pattern))
                            index = found + rule.pattern.length
                        }
                    }
                }
                FilterMode.EQUALS_IGNORE_CASE -> {
                    if (rule.pattern.isEmpty()) return text
                    buildString {
                        var index = 0
                        while (index < text.length) {
                            val found = text.indexOf(rule.pattern, index, ignoreCase = true)
                            if (found < 0) {
                                append(text, index, text.length)
                                break
                            }
                            append(text, index, found)
                            append(mask(text.substring(found, found + rule.pattern.length)))
                            index = found + rule.pattern.length
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            text
        }
    }

    private fun mask(s: String): String = s.map { if (it.isWhitespace()) it else CENSOR_CHAR }.joinToString("")
}
