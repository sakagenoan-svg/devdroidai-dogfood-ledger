package com.dogfood.ledger.domain

import kotlin.math.abs

/**
 * Money stored as integer minor units (e.g. sen/cents) so arithmetic is exact and never
 * suffers floating-point drift. Formatting to a human string happens only at the edge.
 */
@JvmInline
value class Money(val minor: Long) : Comparable<Money> {

    operator fun plus(other: Money) = Money(minor + other.minor)
    operator fun minus(other: Money) = Money(minor - other.minor)
    operator fun unaryMinus() = Money(-minor)

    override fun compareTo(other: Money): Int = minor.compareTo(other.minor)

    val isNegative: Boolean get() = minor < 0

    fun format(currency: String = "¥"): String {
        val sign = if (minor < 0) "-" else ""
        val absolute = abs(minor)
        val major = absolute / 100
        val cents = absolute % 100
        return sign + currency + major.toString() + "." + cents.toString().padStart(2, '0')
    }

    companion object {
        val ZERO = Money(0)
        fun of(major: Long, cents: Int = 0) = Money(major * 100 + cents)
    }
}

fun Iterable<Money>.total(): Money = Money(sumOf { it.minor })
