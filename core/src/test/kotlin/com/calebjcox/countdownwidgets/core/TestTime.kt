package com.calebjcox.countdownwidgets.core

import java.time.LocalDateTime
import java.time.ZoneId

/** Denver is used for daylight saving cases: it observes DST and is not UTC. */
val DENVER: ZoneId = ZoneId.of("America/Denver")
val UTC: ZoneId = ZoneId.of("UTC")

/** Epoch millis for a wall-clock time in [zone], e.g. `"2026-08-09T13:37"`. */
fun at(zone: ZoneId, isoLocal: String): Long =
    LocalDateTime.parse(isoLocal).atZone(zone).toInstant().toEpochMilli()

fun localDateTime(isoLocal: String): LocalDateTime = LocalDateTime.parse(isoLocal)

fun dateSpec(target: String, vararg fields: TimeField): TimerSpec =
    TimerSpec.of(localDateTime(target), Precision.DATE, fields.toSet())

fun dateTimeSpec(target: String, vararg fields: TimeField): TimerSpec =
    TimerSpec.of(localDateTime(target), Precision.DATE_TIME, fields.toSet())

/** The short-form string a widget would show, e.g. `"4mo 16d"`. */
fun shortText(nowMillis: Long, zone: ZoneId, spec: TimerSpec): String =
    Rendering.formatDisplay(DurationMath.compute(nowMillis, zone, spec), LabelStyle.SHORT)
