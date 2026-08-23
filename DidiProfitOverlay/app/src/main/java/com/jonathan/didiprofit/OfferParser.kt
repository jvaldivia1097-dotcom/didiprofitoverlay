package com.jonathan.didiprofit

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max

object OfferParser {
    private val strictFareRegex = Regex("""(?:\$|＄|S)\s*([0-9]{1,4}(?:[.,][0-9]{1,2})?)""")
    private val looseFareRegex = Regex("""(?<!\d)([0-9]{2,4}[.,][0-9]{2})(?!\d)""")

    private val routeRegex = Regex(
        """(?i)([0-9]{1,3})\s*(?:min|minuto|minutos)\s*(?:\(\s*)?([0-9]+(?:[.,][0-9]+)?)\s*(km|m)\b\s*\)?"""
    )
    private val timeOnlyRegex = Regex("""(?i)([0-9]{1,3})\s*(?:min|minuto|minutos)\b""")
    private val distanceOnlyRegex = Regex(
        """(?i)\(?\s*([0-9]+(?:[.,][0-9]+)?)\s*(km|m)\b\s*\)?"""
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

    private val offerMarkers = listOf(
        "pon tu precio",
        "rechazo permitido",
        "punto(s)",
        "perfil avanzado",
        "identificacion verificada",
        "tarjeta bancaria verificada",
        "al precio de express"
    )

    fun isOfferInactive(lines: List<OcrLine>): Boolean {
        if (lines.isEmpty()) return false
        val folded = fold(lines.joinToString(" ") { it.text })
        return inactiveMarkers.any { folded.contains(it) }
    }

    fun looksLikeOffer(lines: List<OcrLine>): Boolean {
        if (lines.isEmpty()) return false
        val expanded = prepareLines(lines)
        val joined = fold(expanded.joinToString(" ") { it.text })
        if (offerMarkers.any { joined.contains(it) }) return true

        val fares = fareCandidates(expanded, strict = true)
        val metrics = routeMetrics(expanded)
        return (fares.isNotEmpty() && metrics.isNotEmpty()) || metrics.size >= 2
    }

    fun mainFareHint(lines: List<OcrLine>): Double? {
        if (lines.isEmpty()) return null
        val expanded = prepareLines(lines)
        chooseLikelyMainFare(fareCandidates(expanded, strict = true))?.let { return it.value }
        return chooseLikelyMainFare(fareCandidates(expanded, strict = false))?.value
    }

    fun parse(lines: List<OcrLine>): RideOffer? {
        if (lines.isEmpty() || isOfferInactive(lines)) return null

        val cleaned = prepareLines(lines)
        val metrics = routeMetrics(cleaned)
        if (metrics.size < 2) {
            return parseSimpleText(cleaned.joinToString("\n") { it.text })
        }

        parseWithCandidates(fareCandidates(cleaned, strict = true), metrics)?.let { return it }
        parseWithCandidates(fareCandidates(cleaned, strict = false), metrics)?.let { return it }

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

    private fun prepareLines(lines: List<OcrLine>): List<OcrLine> {
        val normalized = lines.map { it.copy(text = normalize(it.text)) }
        if (normalized.size < 2) return normalized

        data class Row(val items: MutableList<OcrLine> = mutableListOf())

        val rows = mutableListOf<Row>()
        normalized.sortedBy { it.centerY }.forEach { line ->
            val target = rows.lastOrNull()?.takeIf { row ->
                val avgY = row.items.map { it.centerY }.average()
                val maxHeight = max(
                    line.height,
                    row.items.maxOfOrNull { it.height } ?: line.height
                )
                abs(line.centerY - avgY) <= max(12.0, maxHeight * 0.72)
            }

            if (target != null) {
                target.items += line
            } else {
                rows += Row(mutableListOf(line))
            }
        }

        val synthetic = rows.mapNotNull { row ->
            if (row.items.size < 2) return@mapNotNull null

            val ordered = row.items.sortedWith(
                compareBy<OcrLine> {
                    when {
                        it.left != 0 -> it.left
                        it.centerX != 0 -> it.centerX
                        else -> Int.MAX_VALUE
                    }
                }.thenBy { it.centerY }
            )

            val text = ordered.joinToString(" ") { it.text }.trim()
            if (text.isBlank()) return@mapNotNull null

            OcrLine(
                text = text,
                centerY = ordered.map { it.centerY }.average().toInt(),
                height = ordered.maxOfOrNull { it.height } ?: 0,
                centerX = ordered.map { it.centerX }.filter { it != 0 }
                    .takeIf { it.isNotEmpty() }?.average()?.toInt() ?: 0,
                left = ordered.map { it.left }.filter { it != 0 }.minOrNull() ?: 0,
                right = ordered.map { it.right }.filter { it != 0 }.maxOrNull() ?: 0
            )
        }

        return (normalized + synthetic)
            .distinctBy { "${fold(it.text)}|${it.centerY / 6}|${it.left / 8}|${it.right / 8}" }
            .sortedBy { it.centerY }
    }

    private fun routeMetrics(lines: List<OcrLine>): List<Pair<OcrLine, RouteMetric>> {
        val usable = lines.filterNot { isOverlayLine(it.text) || isSecondaryFareLine(it.text) }

        val direct = usable.mapNotNull { line ->
            parseRouteMetric(line.text)?.let { line to it }
        }.toMutableList()

        val fragments = mutableListOf<Pair<OcrLine, RouteMetric>>()

        usable.forEach { timeLine ->
            if (parseRouteMetric(timeLine.text) != null) return@forEach
            val timeMatch = timeOnlyRegex.find(timeLine.text) ?: return@forEach
            val minutes = timeMatch.groupValues[1].toIntOrNull() ?: return@forEach

            val candidate = usable
                .asSequence()
                .filter { it !== timeLine }
                .filter { parseRouteMetric(it.text) == null }
                .mapNotNull { distanceLine ->
                    val distanceMatch = distanceOnlyRegex.find(distanceLine.text)
                        ?: return@mapNotNull null

                    val yTolerance = max(
                        18.0,
                        max(timeLine.height, distanceLine.height) * 1.25
                    )
                    val yGap = abs(timeLine.centerY - distanceLine.centerY).toDouble()
                    if (yGap > yTolerance) return@mapNotNull null

                    if (timeLine.right != 0 && distanceLine.left != 0 &&
                        distanceLine.right < timeLine.left
                    ) {
                        return@mapNotNull null
                    }

                    val distanceValue = distanceMatch.groupValues[1]
                        .replace(',', '.')
                        .toDoubleOrNull()
                        ?: return@mapNotNull null

                    val km = if (distanceMatch.groupValues[2].equals("m", true)) {
                        distanceValue / 1000.0
                    } else {
                        distanceValue
                    }
                    if (km < 0.0) return@mapNotNull null

                    Triple(distanceLine, RouteMetric(minutes, km), yGap)
                }
                .minByOrNull { it.third }
                ?: return@forEach

            val distanceLine = candidate.first
            val metric = candidate.second
            val synthetic = OcrLine(
                text = "${timeLine.text} ${distanceLine.text}",
                centerY = (timeLine.centerY + distanceLine.centerY) / 2,
                height = max(timeLine.height, distanceLine.height),
                centerX = if (timeLine.centerX != 0 && distanceLine.centerX != 0) {
                    (timeLine.centerX + distanceLine.centerX) / 2
                } else 0,
                left = listOf(timeLine.left, distanceLine.left).filter { it != 0 }.minOrNull() ?: 0,
                right = listOf(timeLine.right, distanceLine.right).filter { it != 0 }.maxOrNull() ?: 0
            )
            fragments += synthetic to metric
        }

        return (direct + fragments)
            .distinctBy { (line, metric) ->
                "${metric.minutes}|${"%.3f".format(metric.kilometers)}|${line.centerY / 8}"
            }
            .sortedBy { it.first.centerY }
    }

    private fun fareCandidates(lines: List<OcrLine>, strict: Boolean): List<FareCandidate> =
        lines.mapNotNull { fareCandidate(it, strict) }

    private fun chooseLikelyMainFare(inputFares: List<FareCandidate>): FareCandidate? {
        if (inputFares.isEmpty()) return null
        val maxHeight = inputFares.maxOfOrNull { it.height } ?: 0
        val likely = if (maxHeight > 0) {
            inputFares.filter { it.height >= max(1, (maxHeight * 0.68).toInt()) }
        } else inputFares

        return likely.ifEmpty { inputFares }
            .sortedWith(compareByDescending<FareCandidate> { it.height }.thenBy { it.y })
            .firstOrNull()
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
