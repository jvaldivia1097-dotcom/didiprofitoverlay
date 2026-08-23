package com.jonathan.didiprofit

import java.text.Normalizer
import kotlin.math.max

/**
 * Parser tuned for the current DiDi Conductor offer layouts used in Mexico.
 *
 * V3.1:
 * - ignores overlay-generated route-summary lines
 * - tolerates OCR losing/misreading the "$" symbol on the main fare
 * - keeps secondary monetary rows ("adicionales", "tarifa base dinámica", etc.) out
 * - preserves geometry-aware grouping for carousel and full-screen offers
 */
object OfferParser {
    private val strictFareRegex = Regex("""(?:\$|＄|S)\s*([0-9]{1,4}(?:[.,][0-9]{1,2})?)""")
    private val looseFareRegex = Regex("""(?<!\d)([0-9]{2,4}[.,][0-9]{2})(?!\d)""")

    /**
     * Supports:
     * - Carousel: "(5 min 648 m)" / "(17 min 6.8 km)"
     * - Full-screen: "6min (2.7km)" / "15min (6.9km)"
     * - OCR variants with optional parentheses/spaces.
     */
    private val routeRegex = Regex(
        """(?i)([0-9]{1,3})\s*(?:min|minuto|minutos)\s*(?:\(\s*)?([0-9]+(?:[.,][0-9]+)?)\s*(km|m)\s*\)?"""
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

    private val secondaryFareMarkers = listOf(
        "adicional",
        "tarifa base dinamica",
        "tarifa base",
        "de tarifa mas rapido",
        "tarifa mas rapido",
        "aceptar $",
        "tomar viaje"
    )

    fun isOfferInactive(lines: List<OcrLine>): Boolean {
        if (lines.isEmpty()) return false
        val folded = fold(lines.joinToString(" ") { it.text })
        return inactiveMarkers.any { folded.contains(it) }
    }

    fun parse(lines: List<OcrLine>): RideOffer? {
        if (lines.isEmpty() || isOfferInactive(lines)) return null

        val cleaned = lines.map { it.copy(text = normalize(it.text)) }

        // Important V3.1 fix: never use route totals printed by our own overlay
        // as pickup/trip rows for the next DiDi proposal.
        val metrics = cleaned.mapNotNull { line ->
            if (isOverlayLine(line.text) || isSecondaryFareLine(line.text)) {
                null
            } else {
                parseRouteMetric(line.text)?.let { line to it }
            }
        }.sortedBy { it.first.centerY }

        if (metrics.size < 2) {
            return parseSimpleText(cleaned.joinToString("\n") { it.text })
        }

        val strictFares = cleaned.mapNotNull { line ->
            fareCandidate(line, strict = true)
        }

        parseWithCandidates(strictFares, metrics)?.let { return it }

        // If ML Kit dropped the "$" symbol (common on large white fare text),
        // try a conservative geometry/size-based numeric fare candidate.
        val looseFares = cleaned.mapNotNull { line ->
            fareCandidate(line, strict = false)
        }

        parseWithCandidates(looseFares, metrics)?.let { return it }

        return parseSimpleText(cleaned.joinToString("\n") { it.text })
    }

    fun parseSimpleText(raw: String): RideOffer? {
        val normalized = normalize(raw)
        if (inactiveMarkers.any { fold(normalized).contains(it) }) return null

        val lines = normalized.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val metricMatches = lines
            .filterNot { isOverlayLine(it) || isSecondaryFareLine(it) }
            .flatMap { routeRegex.findAll(it).toList() }

        if (metricMatches.size < 2) return null

        val fare = firstFareFromLines(lines, strict = true)
            ?: firstFareFromLines(lines, strict = false)
            ?: return null

        val pickup = metricFromMatch(metricMatches[0]) ?: return null
        val trip = metricFromMatch(metricMatches[1]) ?: return null
        return RideOffer(fare, pickup, trip)
    }

    private fun parseWithCandidates(
        inputFares: List<FareCandidate>,
        metrics: List<Pair<OcrLine, RouteMetric>>
    ): RideOffer? {
        if (inputFares.isEmpty()) return null

        val maxHeight = inputFares.maxOfOrNull { it.height } ?: 0
        val likelyMainFares = if (maxHeight > 0) {
            inputFares.filter { it.height >= max(1, (maxHeight * 0.68).toInt()) }
        } else {
            inputFares
        }

        val candidates = likelyMainFares.ifEmpty { inputFares }.sortedBy { it.y }

        for ((index, fare) in candidates.withIndex()) {
            val nextFareY = candidates.getOrNull(index + 1)?.y ?: Int.MAX_VALUE
            val below = metrics.filter { (line, _) ->
                line.centerY >= fare.y && line.centerY < nextFareY
            }.take(2)

            if (below.size >= 2) {
                return RideOffer(fare.value, below[0].second, below[1].second)
            }
        }
        return null
    }

    private fun fareCandidate(line: OcrLine, strict: Boolean): FareCandidate? {
        if (isOverlayLine(line.text) || isSecondaryFareLine(line.text)) return null
        if (parseRouteMetric(line.text) != null) return null

        val folded = fold(line.text)
        if (folded.contains("viajes") || folded.contains("punto") || folded.contains("rating")) {
            return null
        }

        val match = (if (strict) strictFareRegex.find(line.text) else looseFareRegex.find(line.text))
            ?: return null

        val value = match.groupValues[1]
            .replace(',', '.')
            .toDoubleOrNull()
            ?: return null

        if (value !in 10.0..3000.0) return null
        return FareCandidate(value, line.centerY, line.height, line.text)
    }

    private fun firstFareFromLines(lines: List<String>, strict: Boolean): Double? {
        for (line in lines) {
            if (isOverlayLine(line) || isSecondaryFareLine(line)) continue
            if (parseRouteMetric(line) != null) continue

            val folded = fold(line)
            if (folded.contains("viajes") || folded.contains("punto") || folded.contains("rating")) {
                continue
            }

            val match = (if (strict) strictFareRegex.find(line) else looseFareRegex.find(line))
                ?: continue
            val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: continue
            if (value in 10.0..3000.0) return value
        }
        return null
    }

    private fun parseRouteMetric(text: String): RouteMetric? {
        val match = routeRegex.find(text) ?: return null
        return metricFromMatch(match)
    }

    private fun metricFromMatch(match: MatchResult): RouteMetric? {
        val minutes = match.groupValues[1].toIntOrNull() ?: return null
        val distanceValue = match.groupValues[2]
            .replace(',', '.')
            .toDoubleOrNull()
            ?: return null

        val kilometers = if (match.groupValues[3].equals("m", ignoreCase = true)) {
            distanceValue / 1000.0
        } else {
            distanceValue
        }

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

    private fun isSecondaryFareLine(text: String): Boolean {
        val folded = fold(text)
        return secondaryFareMarkers.any { folded.contains(it) }
    }

    private fun isOverlayLine(text: String): Boolean {
        val lower = fold(text)
        return lower.contains("/h") ||
            lower.contains("/km") ||
            lower.contains("rentabilidad") ||
            lower.contains("analizando didi") ||
            lower.contains("esperando propuesta") ||
            lower.contains("incluye recogida") ||
            lower.contains("pon tu precio minimo") ||
            lower.contains("puntaje") ||
            (lower.contains("tarifa") && lower.contains("min") && lower.contains("km"))
    }

    private data class FareCandidate(
        val value: Double,
        val y: Int,
        val height: Int,
        val text: String
    )
}
