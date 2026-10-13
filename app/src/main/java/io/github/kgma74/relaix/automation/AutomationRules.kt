package io.github.kgma74.relaix.automation

import smsgateway.v1.Device
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Matching, extraction and templates, as the gateway defines them
 * (server/policy/automation.go). The shared cases in
 * server/policy/testdata/automation_vectors.json are run against this too.
 */
object AutomationRules {
    val BUILTINS = listOf("sender", "body", "receivedAt", "sim")
    const val PIN = "pin"

    private val placeholder = Regex("""\{([A-Za-z][A-Za-z0-9]*)\}""")
    private val groupName = Regex("""\(\?P?<([^>]*)>""")

    /** The fields the automation yields for this SMS, or null when it does not apply. */
    fun match(a: Device.Automation, sender: String, body: String, receivedAt: String, sim: Int): Map<String, String>? {
        if (!a.enabled) return null
        if (a.subscriptionId != 0 && a.subscriptionId != sim) return null
        if (a.sendersCount > 0 && a.sendersList.none { it.equals(sender.trim(), ignoreCase = true) }) return null

        val fields = linkedMapOf("sender" to sender, "body" to body, "receivedAt" to receivedAt, "sim" to sim.toString())
        if (a.pattern.isNotEmpty()) {
            val pattern = compile(a.pattern) ?: return null
            val m = pattern.matcher(body)
            if (!m.find()) return null
            // Pattern.namedGroups() is API 34+: the names are read from the
            // expression instead, which the gateway restricted to [A-Za-z][A-Za-z0-9]*.
            for (name in groupName.findAll(a.pattern).map { it.groupValues[1] }) {
                fields[name] = runCatching { m.group(name) }.getOrNull().orEmpty()
            }
        }
        for (c in a.conditionsList) if (!holds(c, fields[c.field].orEmpty())) return null
        return fields
    }

    /** RE2's (?P<name>) spelled as Java's (?<name>). */
    fun compile(pattern: String): Pattern? = try {
        Pattern.compile(pattern.replace("(?P<", "(?<"))
    } catch (_: PatternSyntaxException) {
        null
    }

    private fun holds(c: Device.AutomationCondition, v: String): Boolean = when (c.op) {
        "eq" -> v.equals(c.value, ignoreCase = true)
        "ne" -> !v.equals(c.value, ignoreCase = true)
        "contains" -> v.contains(c.value, ignoreCase = true)
        else -> {
            val got = number(v)
            val want = number(c.value)
            if (got == null || want == null) {
                false
            } else {
                when (c.op) {
                    "gt" -> got > want
                    "gte" -> got >= want
                    "lt" -> got < want
                    "lte" -> got <= want
                    else -> false
                }
            }
        }
    }

    /** "5 000" → 5000.0, "10,25" → 10.25. */
    fun number(s: String): Double? =
        s.filterNot { it == ' ' || it == '\u00a0' || it == '\u202f' }.replace(',', '.').toDoubleOrNull()

    /** Fills {name}; [keepPin] leaves {pin} for the vault to fill. */
    fun render(template: String, fields: Map<String, String>, keepPin: Boolean = false): String =
        placeholder.replace(template) { m ->
            val name = m.groupValues[1]
            if (name == PIN && keepPin) m.value else fields[name].orEmpty()
        }

    /**
     * Fills a USSD code: a field read as "5 000" goes in as 5000, since a
     * code has no spaces. {pin} is left for the vault.
     */
    fun renderUssd(template: String, fields: Map<String, String>): String =
        render(template, fields.mapValues { (_, v) -> v.filterNot { it.isWhitespace() || it == '\u00a0' || it == '\u202f' } }, keepPin = true)
}
