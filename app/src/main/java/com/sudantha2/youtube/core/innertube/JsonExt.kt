package com.sudantha2.youtube.core.innertube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Allocation-lean navigation helpers over kotlinx.serialization's JsonObject
 * tree. InnerTube responses are deep and sparse; these inline-style lookups
 * walk only the paths we need, never reflecting generated serializers over
 * the entire tree. Every helper is null-tolerant so parser code stays flat.
 */

internal fun JsonElement?.asObj(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asArr(): JsonArray? = this as? JsonArray

internal fun JsonElement?.asStr(): String? = (this as? JsonPrimitive)?.contentOrNull

internal fun JsonElement?.asInt(): Int? = (this as? JsonPrimitive)?.intOrNull

internal fun JsonObject?.obj(key: String): JsonObject? = this?.get(key).asObj()

internal fun JsonObject?.arr(key: String): JsonArray? = this?.get(key).asArr()

internal fun JsonObject?.str(key: String): String? = this?.get(key).asStr()

internal fun JsonObject?.int(key: String): Int? = this?.get(key).asInt()

/** Concatenated text of a `runs` array (`{"runs":[{"text":…},…]}`). */
internal fun JsonObject?.runsText(key: String): String? {
    val runs = arr(key) ?: return this.str(key) // fall back to simpleText
    if (runs.isEmpty()) return null
    val sb = StringBuilder()
    for (run in runs) {
        run.asObj()?.str("text")?.let(sb::append)
    }
    return sb.toString().ifEmpty { null }
}

/** Largest thumbnail URL in a `thumbnail.thumbnails[]` array. */
internal fun JsonObject?.bestThumbnailUrl(key: String = "thumbnail"): String? {
    val arr = arr(key) ?: return null
    var best: String? = null
    var bestWidth = -1
    for (t in arr) {
        val o = t.asObj() ?: continue
        val w = o.int("width") ?: 0
        if (w >= bestWidth) {
            bestWidth = w
            best = o.str("url") ?: best
        }
    }
    return best
}
