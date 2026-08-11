package com.jonathan.didiprofit

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OfferParserTest {
    @Test
    fun parsesKmExampleFromDidi() {
        val offer = assertNotNull(OfferParser.parseSimpleText(
            """
            Centro de viajes
            Tarifa
            $34.32
            (9 min 3.9 km) Calle 5 317, Altamira
            (11 min 3.7 km) Bahuinia 308, Altamira
            Tomar viaje
            """.trimIndent()
        ))
        assertTrue(abs(offer.totalKilometers - 7.6) < 0.001)
        assertTrue(offer.totalMinutes == 20)
        assertTrue(abs(offer.pesosPerHour - 102.96) < 0.01)
    }

    @Test
    fun convertsMetersToKm() {
        val offer = assertNotNull(OfferParser.parseSimpleText(
            """
            Pon Tu Precio Nuevo
            $39.95
            (3 min 277 m) Grupo La Red de Altamira
            1 parada(s)
            (13 min 4.6 km) Guerrero LB LB, Altamira
            Aceptar $39.95
            """.trimIndent()
        ))
        assertTrue(abs(offer.totalKilometers - 4.877) < 0.001)
        assertTrue(offer.totalMinutes == 16)
        assertTrue(abs(offer.pesosPerKm - 8.1915) < 0.01)
    }

    @Test
    fun parsesAnotherDidiExample() {
        val offer = assertNotNull(OfferParser.parseSimpleText(
            """
            Pon Tu Precio Nuevo
            $67.44
            (10 min 2.5 km) Citibanamex Altamira
            (11 min 4.6 km) Patio De Auto Express Dago Altamira
            Aceptar $67.44
            """.trimIndent()
        ))
        assertTrue(offer.totalMinutes == 21)
        assertTrue(abs(offer.totalKilometers - 7.1) < 0.001)
        assertTrue(abs(offer.pesosPerHour - 192.6857) < 0.01)
    }
}
