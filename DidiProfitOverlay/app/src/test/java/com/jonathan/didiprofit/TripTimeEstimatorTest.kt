package com.jonathan.didiprofit

import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TripTimeEstimatorTest {
    @Test
    fun fifteenMinutesFromTenEndsAtTenFifteen() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 26, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val result = assertNotNull(TripTimeEstimator.estimatedFinishMillis(15, calendar.timeInMillis))
        val finish = Calendar.getInstance().apply { timeInMillis = result }
        assertEquals(10, finish.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, finish.get(Calendar.MINUTE))
    }

    @Test
    fun crossesMidnightCorrectly() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 26, 23, 55, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val result = assertNotNull(TripTimeEstimator.estimatedFinishMillis(10, calendar.timeInMillis))
        val finish = Calendar.getInstance().apply { timeInMillis = result }
        assertEquals(0, finish.get(Calendar.HOUR_OF_DAY))
        assertEquals(5, finish.get(Calendar.MINUTE))
        assertEquals(27, finish.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun invalidDurationsAreRejected() {
        assertNull(TripTimeEstimator.estimatedFinishMillis(0, 1000L))
        assertNull(TripTimeEstimator.estimatedFinishMillis(-5, 1000L))
        assertNull(TripTimeEstimator.estimatedFinishMillis(361, 1000L))
    }
}
