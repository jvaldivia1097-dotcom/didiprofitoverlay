package com.jonathan.didiprofit

import java.text.Normalizer
import kotlin.math.max

/** Parser tuned for the current DiDi Conductor offer layout used in Mexico. */
object OfferParser {
    private val fareRegex = Regex("\\$\\s*([0-9]{1,4}(?:[.,][0-9]{1,2})?)")
    private val routeRegex = Regex(
        "(?i)([0-9]{1,3})\\s*(?:min|minuto|minutos)\\s*([0-9]+(?:[.,][0-9]+)?)\\s*(km|m)\\b"
    )

    private val inactiveMarkers = listOf(
        "otro conductor acepto el viaje",
        "otro conductor tomo el viaje",
        "no hay mas solicitudes",
        "viaje ya no esta disponible",
        "viaje no disponible",
        "solicitud ya no esta disponible",
        "este viaje ya fue aceptado"
    )

    fun isOfferInactive(lines: List<OcrLine>): Boolean {
        if (lines.isEmpty()) return false
        val folded = fold(lines.joinToString(" ") { it.text })
        return inactiveMarkers.any { folded.contains(it) }
    }

    fun parse(lines: List<OcrLine>): RideOffer? {
        if (lines.isEmpty() || isOfferInactive(lines)) return null

        val cleaned = lines.map { it.copy(text = normalize(it.text)) }
        val metrics = cleaned.mapNotNull { line ->
            parseRouteMetric(line.text)?.let { line to it }
        }.sortedBy { it.first.centerY }

        if (metrics.size < 2) return null

        val fares = cleaned.mapNotNull { line ->
            if (isOverlayLine(line.text)) return@mapNotNull null
            val match = fareRegex.find(line.text) ?: return@mapNotNull null
            val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
            FareCandidate(value, line.centerY, line.height, line.text)
        }

        if (fares.isEmpty()) return null

        val maxHeight = fares.maxOfOrNull { it.height } ?: 0
        val likelyMainFares = if (maxHeight > 0) {
            fares.filter { it.height >= max(1, (maxHeight * 0.72).toInt()) }
        } else fares

        val candidates = likelyMainFares.ifEmpty { fares }.sortedBy { it.y }

        for ((index, fare) in candidates.withIndex()) {
            val nextFareY = candidates.getOrNull(index + 1)?.y ?: Int.MAX_VALUE
            val below = metrics.filter { (line, _) ->
                line.centerY >= fare.y && line.centerY < nextFareY
            }.take(2)
            if (below.size >= 2) {
                return RideOffer(fare.value, below[0].second, below[1].second)
            }
        }

        return parseSimpleText(cleaned.joinToString("\n") { it.text })
    }

    fun parseSimpleText(raw: String): RideOffer? {
        val text = normalize(raw)
        if (inactiveMarkers.any { fold(text).contains(it) }) return null

        val metricMatches = routeRegex.findAll(text).toList()
        if (metricMatches.size < 2) return null

        val fare = fareRegex.findAll(text)
            .firstOrNull { !isOverlayLine(contextLine(text, it.range.first)) }
            ?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
            ?: return null

        val pickup = metricFromMatch(metricMatches[0]) ?: return null
        val trip = metricFromMatch(metricMatches[1]) ?: return null
        return RideOffer(fare, pickup, trip)
    }

    private fun parseRouteMetric(text: String): RouteMetric? {
        val match = routeRegex.find(text) ?: return null
        return metricFromMatch(match)
    }

    private fun metricFromMatch(match: MatchResult): RouteMetric? {
        val minutes = match.groupValues[1].toIntOrNull() ?: return null
        val distanceValue = match.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return null
        val kilometers = if (match.groupValues[3].equals("m", ignoreCase = true)) {
            distanceValue / 1000.0
        } else distanceValue
        if (minutes <= 0 || kilometers < 0.0) return null
        return RouteMetric(minutes, kilometers)
    }

    private fun normalize(input: String): String {
        return Normalizer.normalize(input, Normalizer.Form.NFKC)
            .replace('·', ' ')
            .replace('•', ' ')
            .replace(Regex("[ \\t]+"), " ")
    }

    private fun fold(input: String): String {
        return Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
    }

    private fun isOverlayLine(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("/h") || lower.contains("/km") ||
            lower.contains("rentabilidad") || lower.contains("analizando didi") ||
            lower.contains("pon tu precio · mínimo")
    }

    private fun contextLine(text: String, index: Int): String {
        val start = text.lastIndexOf('\n', maxOf(0, index - 1)).let { if (it < 0) 0 else it + 1 }
        val end = text.indexOf('\n', index).let { if (it < 0) text.length else it }
        return text.substring(start, end)
    }

    private data class FareCandidate(
        val value: Double,
        val y: Int,
        val height: Int,
        val text: String
    )
}
