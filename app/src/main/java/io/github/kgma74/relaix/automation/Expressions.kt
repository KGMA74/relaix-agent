package io.github.kgma74.relaix.automation

import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.floor

/**
 * Calculations in templates: {amount * 2}, {amount - 100}, {(amount + fee) * 0.98}.
 * Fields read as amounts, + - * / and parentheses. The gateway's
 * policy/expr.go, rule for rule: same parsing, same rounding (half away from
 * zero), same shortest decimal; the shared vectors hold the three to it.
 */
object Expressions {
    private val fieldName = Regex("^[A-Za-z][A-Za-z0-9]*$")
    private val operator = Regex("[+\\-*/]")
    private const val MAX_LEN = 100

    /** A calculation in braces: not a field name, and an operator in it. */
    fun isExpression(inner: String): Boolean = !fieldName.matches(inner) && operator.containsMatchIn(inner)

    /** The result rounded to [places], or null when it cannot be computed. */
    fun eval(s: String, fields: Map<String, String>, places: Int): String? {
        if (s.length > MAX_LEN) return null
        val v = try {
            Parser(s) { name -> AutomationRules.number(fields[name].orEmpty()) ?: throw ArithmeticException("not a number") }.parse()
        } catch (_: ArithmeticException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } ?: return null
        if (v.isNaN() || v.isInfinite()) return null
        return format(round(v, places))
    }

    fun round(v: Double, places: Int): Double {
        var p = 1.0
        repeat(places) { p *= 10 }
        val r = floor(abs(v) * p + 0.5) / p
        return if (v < 0) -r else r
    }

    /** The shortest decimal: 400, 66.67. */
    fun format(v: Double): String =
        if (v == 0.0) "0" else BigDecimal(v.toString()).stripTrailingZeros().toPlainString()

    private class Parser(val s: String, val get: (String) -> Double) {
        var i = 0

        fun parse(): Double {
            val v = sum()
            peek()
            require(i == s.length)
            return v
        }

        fun peek(): Char {
            while (i < s.length && s[i] == ' ') i++
            return if (i < s.length) s[i] else '\u0000'
        }

        // Evaluated left to right, as the gateway does: a field that is not a
        // number fails the calculation wherever it stands.
        fun sum(): Double {
            var left = product()
            while (true) {
                val op = peek()
                if (op != '+' && op != '-') return left
                i++
                val right = product()
                left = if (op == '+') left + right else left - right
            }
        }

        fun product(): Double {
            var left = unary()
            while (true) {
                val op = peek()
                if (op != '*' && op != '/') return left
                i++
                val right = unary()
                left = if (op == '*') {
                    left * right
                } else {
                    if (right == 0.0) throw ArithmeticException("division by zero")
                    left / right
                }
            }
        }

        fun unary(): Double {
            if (peek() == '-') {
                i++
                return -unary()
            }
            return atom()
        }

        fun atom(): Double {
            val c = peek()
            val start = i
            return when {
                c == '(' -> {
                    i++
                    val v = sum()
                    require(peek() == ')')
                    i++
                    v
                }
                c in '0'..'9' -> {
                    while (i < s.length && (s[i] in '0'..'9' || s[i] == '.')) i++
                    s.substring(start, i).toDouble()
                }
                c in 'A'..'Z' || c in 'a'..'z' -> {
                    while (i < s.length && (s[i] in 'A'..'Z' || s[i] in 'a'..'z' || s[i] in '0'..'9')) i++
                    get(s.substring(start, i))
                }
                else -> throw IllegalArgumentException("invalid expression")
            }
        }
    }
}
