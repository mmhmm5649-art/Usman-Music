package com.usman.musicplayer.data.listentogether

import kotlin.math.abs

/**
 * NTP-style 4-timestamp clock offset and RTT estimator.
 *
 * Given:
 * - t0 = clientSendTimeMs
 * - t1 = serverReceiveTimeMs
 * - t2 = serverSendTimeMs
 * - t3 = clientReceiveTimeMs
 *
 * RTT    = (t3 - t0) - (t2 - t1)
 * Offset = ((t1 - t0) + (t2 - t3)) / 2   (serverTime ~= clientTime + offset)
 *
 * Uses a rolling window with outlier rejection (discarding high-RTT spike samples)
 * and median filtering to stay immune to mobile network jitter.
 */
class ClockSyncEngine(
    private val maxSamples: Int = 9,
    private val localNowProvider: () -> Long = { System.currentTimeMillis() }
) {
    data class Sample(
        val offsetMs: Long,
        val rttMs: Long
    )

    private val lock = Any()
    private val samples = ArrayDeque<Sample>(maxSamples)

    @Volatile
    var clockOffsetMs: Long = 0L
        private set

    @Volatile
    var estimatedRttMs: Long = 0L
        private set

    @Volatile
    var sampleCount: Int = 0
        private set

    val isSynced: Boolean
        get() = sampleCount > 0

    fun recordSample(
        clientSendTimeMs: Long,
        serverReceiveTimeMs: Long,
        serverSendTimeMs: Long,
        clientReceiveTimeMs: Long = localNowProvider()
    ): Sample {
        val rawRtt = (clientReceiveTimeMs - clientSendTimeMs) - (serverSendTimeMs - serverReceiveTimeMs)
        val rtt = rawRtt.coerceAtLeast(0L)
        val offset = ((serverReceiveTimeMs - clientSendTimeMs) + (serverSendTimeMs - clientReceiveTimeMs)) / 2L
        val sample = Sample(offsetMs = offset, rttMs = rtt)

        synchronized(lock) {
            if (samples.size >= maxSamples) {
                samples.removeFirst()
            }
            samples.addLast(sample)
            sampleCount = samples.size
            recomputeLocked()
        }
        return sample
    }

    /**
     * Quick coarse fallback when a server message arrives before the first pong finishes.
     */
    fun seedCoarseOffsetIfUnsynced(serverTimeMs: Long, localTimeMs: Long = localNowProvider()) {
        synchronized(lock) {
            if (samples.isEmpty() && serverTimeMs > 0L) {
                clockOffsetMs = serverTimeMs - localTimeMs
            }
        }
    }

    fun estimatedServerTimeMs(localTimeMs: Long = localNowProvider()): Long {
        return localTimeMs + clockOffsetMs
    }

    fun serverToLocalTimeMs(serverTimeMs: Long): Long {
        return serverTimeMs - clockOffsetMs
    }

    fun reset() {
        synchronized(lock) {
            samples.clear()
            sampleCount = 0
            clockOffsetMs = 0L
            estimatedRttMs = 0L
        }
    }

    private fun recomputeLocked() {
        if (samples.isEmpty()) return
        val list = samples.toList()
        if (list.size <= 2) {
            val best = list.minBy { it.rttMs }
            clockOffsetMs = best.offsetMs
            estimatedRttMs = best.rttMs
            return
        }

        // Discard high-RTT outliers (keep fastest ~60% of samples, minimum 3)
        val sortedByRtt = list.sortedBy { it.rttMs }
        val keepCount = ((sortedByRtt.size * 3) / 5).coerceAtLeast(3).coerceAtMost(sortedByRtt.size)
        val lowLatencySubset = sortedByRtt.take(keepCount)

        val sortedOffsets = lowLatencySubset.map { it.offsetMs }.sorted()
        val medianOffset = sortedOffsets[sortedOffsets.size / 2]

        // Reject any residual offset spike > 250ms from median if enough samples remain
        val cleanOffsets = sortedOffsets.filter { abs(it - medianOffset) <= 250L }
            .ifEmpty { sortedOffsets }

        clockOffsetMs = cleanOffsets[cleanOffsets.size / 2]
        estimatedRttMs = lowLatencySubset.map { it.rttMs }.sorted()[lowLatencySubset.size / 2]
    }
}
