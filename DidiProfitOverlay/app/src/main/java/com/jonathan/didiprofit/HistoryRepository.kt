package com.jonathan.didiprofit

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlin.math.abs

data class HistoryEntry(
    val id: Long,
    val fare: Double,
    val pickupMinutes: Int,
    val pickupKm: Double,
    val tripMinutes: Int,
    val tripKm: Double,
    val pesosPerHour: Double,
    val pesosPerKm: Double,
    val score: Int,
    val firstSeen: Long,
    val lastSeen: Long,
    val seenCount: Int
) {
    val totalMinutes: Int get() = pickupMinutes + tripMinutes
    val totalKm: Double get() = pickupKm + tripKm
}

data class HistoryStats(
    val count: Int = 0,
    val avgHourly: Double = 0.0,
    val avgKm: Double = 0.0,
    val avgScore: Double = 0.0,
    val bestHourly: Double = 0.0,
    val bestKm: Double = 0.0
)

class HistoryRepository(context: Context) {
    private val dbHelper = Db(context.applicationContext)

    /**
     * Returns true when a new unique proposal was inserted.
     * Returns false when it was merged with a recently-seen duplicate.
     */
    @Synchronized
    fun recordOrMerge(
        offer: RideOffer,
        thresholds: Thresholds,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val db = dbHelper.writableDatabase
        val since = now - DEDUP_WINDOW_MS

        val cursor = db.query(
            TABLE,
            null,
            "last_seen >= ?",
            arrayOf(since.toString()),
            null,
            null,
            "last_seen DESC",
            "40"
        )

        cursor.use {
            while (it.moveToNext()) {
                val existing = row(it)
                if (sameProposal(existing, offer)) {
                    val values = valuesFor(offer, thresholds, now).apply {
                        put("first_seen", existing.firstSeen)
                        put("seen_count", existing.seenCount + if (now - existing.lastSeen >= REAPPEAR_GAP_MS) 1 else 0)
                    }
                    db.update(TABLE, values, "id = ?", arrayOf(existing.id.toString()))
                    return false
                }
            }
        }

        val values = valuesFor(offer, thresholds, now).apply {
            put("first_seen", now)
            put("seen_count", 1)
        }
        db.insert(TABLE, null, values)
        return true
    }

    fun recent(limit: Int = 75): List<HistoryEntry> {
        val result = ArrayList<HistoryEntry>()
        dbHelper.readableDatabase.query(
            TABLE,
            null,
            null,
            null,
            null,
            null,
            "last_seen DESC",
            limit.coerceIn(1, 300).toString()
        ).use {
            while (it.moveToNext()) result += row(it)
        }
        return result
    }

    fun statsSince(since: Long = 0L): HistoryStats {
        val sql = """
            SELECT COUNT(*) AS c,
                   COALESCE(AVG(pph), 0) AS ah,
                   COALESCE(AVG(ppkm), 0) AS ak,
                   COALESCE(AVG(score), 0) AS ascore,
                   COALESCE(MAX(pph), 0) AS bh,
                   COALESCE(MAX(ppkm), 0) AS bk
            FROM $TABLE
            WHERE last_seen >= ?
        """.trimIndent()

        dbHelper.readableDatabase.rawQuery(sql, arrayOf(since.toString())).use {
            if (!it.moveToFirst()) return HistoryStats()
            return HistoryStats(
                count = it.getInt(0),
                avgHourly = it.getDouble(1),
                avgKm = it.getDouble(2),
                avgScore = it.getDouble(3),
                bestHourly = it.getDouble(4),
                bestKm = it.getDouble(5)
            )
        }
    }

    fun clearAll() {
        dbHelper.writableDatabase.delete(TABLE, null, null)
    }

    private fun sameProposal(old: HistoryEntry, offer: RideOffer): Boolean {
        return abs(old.fare - offer.fare) <= 0.02 &&
            abs(old.pickupMinutes - offer.pickup.minutes) <= 1 &&
            abs(old.tripMinutes - offer.trip.minutes) <= 1 &&
            abs(old.pickupKm - offer.pickup.kilometers) <= 0.60 &&
            abs(old.tripKm - offer.trip.kilometers) <= 0.25
    }

    private fun valuesFor(
        offer: RideOffer,
        thresholds: Thresholds,
        now: Long
    ) = ContentValues().apply {
        put("fare", offer.fare)
        put("pickup_minutes", offer.pickup.minutes)
        put("pickup_km", offer.pickup.kilometers)
        put("trip_minutes", offer.trip.minutes)
        put("trip_km", offer.trip.kilometers)
        put("pph", offer.pesosPerHour)
        put("ppkm", offer.pesosPerKm)
        put("score", offer.profitabilityScore(thresholds))
        put("last_seen", now)
    }

    private fun row(c: android.database.Cursor) = HistoryEntry(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        fare = c.getDouble(c.getColumnIndexOrThrow("fare")),
        pickupMinutes = c.getInt(c.getColumnIndexOrThrow("pickup_minutes")),
        pickupKm = c.getDouble(c.getColumnIndexOrThrow("pickup_km")),
        tripMinutes = c.getInt(c.getColumnIndexOrThrow("trip_minutes")),
        tripKm = c.getDouble(c.getColumnIndexOrThrow("trip_km")),
        pesosPerHour = c.getDouble(c.getColumnIndexOrThrow("pph")),
        pesosPerKm = c.getDouble(c.getColumnIndexOrThrow("ppkm")),
        score = c.getInt(c.getColumnIndexOrThrow("score")),
        firstSeen = c.getLong(c.getColumnIndexOrThrow("first_seen")),
        lastSeen = c.getLong(c.getColumnIndexOrThrow("last_seen")),
        seenCount = c.getInt(c.getColumnIndexOrThrow("seen_count"))
    )

    private class Db(context: Context) :
        SQLiteOpenHelper(context, "ride_history.db", null, 1) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    fare REAL NOT NULL,
                    pickup_minutes INTEGER NOT NULL,
                    pickup_km REAL NOT NULL,
                    trip_minutes INTEGER NOT NULL,
                    trip_km REAL NOT NULL,
                    pph REAL NOT NULL,
                    ppkm REAL NOT NULL,
                    score INTEGER NOT NULL,
                    first_seen INTEGER NOT NULL,
                    last_seen INTEGER NOT NULL,
                    seen_count INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_history_last_seen ON $TABLE(last_seen)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object {
        private const val TABLE = "offers"
        private const val DEDUP_WINDOW_MS = 10L * 60L * 1000L
        private const val REAPPEAR_GAP_MS = 15L * 1000L
    }
}
