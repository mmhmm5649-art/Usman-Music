package com.usman.musicplayer.data.listentogether

import kotlin.math.abs

/**
 * Pure, deterministic playback synchronization & drift correction controller.
 *
 * Strategy:
 * - Future scheduled start (`serverNow < scheduledStartServerTimeMs`): hold paused at `basePositionMs`
 *   and schedule exact resume at `delayMs = scheduledStartServerTimeMs - serverNow`.
 * - Small drift within deadband (`|drift| <= 80ms`): keep playback speed at 1.0x (no audible glitch).
 * - Moderate drift (`80ms < |drift| <= 450ms`): nudge playback speed smoothly (`0.96x` or `1.04x`)
 *   instead of hard-seeking so audio stays silky smooth.
 * - Large drift (`|drift| > 450ms`): perform a safe seek to exact authoritative position,
 *   protected by a 1500ms seek cooldown to prevent thrashing on jittery mobile links.
 */
class PlaybackDriftController(
    private val deadbandMs: Long = 80L,
    private val softCorrectionMaxDriftMs: Long = 450L,
    private val seekCooldownMs: Long = 1500L,
    private val slowDownSpeed: Float = 0.96f,
    private val catchUpSpeed: Float = 1.04f
) {
    sealed class CorrectionAction {
        data object InSync : CorrectionAction()
        data class ScheduleStart(
            val targetPositionMs: Long,
            val delayMs: Long
        ) : CorrectionAction()

        data class PauseAt(val targetPositionMs: Long) : CorrectionAction()
        data class AdjustSpeed(val speed: Float, val driftMs: Long) : CorrectionAction()
        data class HardSeek(val targetPositionMs: Long, val driftMs: Long) : CorrectionAction()
    }

    private var lastHardSeekLocalMs: Long = 0L

    fun reset() {
        lastHardSeekLocalMs = 0L
    }

    /**
     * Projects the authoritative track position for a given server timestamp.
     */
    fun projectTargetPositionMs(
        state: AuthoritativePlaybackState,
        serverNowMs: Long,
        trackDurationMs: Long
    ): Long {
        val raw = if (!state.isPlaying) {
            state.basePositionMs
        } else {
            val startAnchor = if (state.scheduledStartServerTimeMs > 0L) {
                state.scheduledStartServerTimeMs
            } else {
                state.anchorServerTimeMs
            }
            if (serverNowMs < startAnchor) {
                state.basePositionMs
            } else {
                val elapsed = ((serverNowMs - startAnchor) * state.playbackRate.coerceIn(0.5f, 2.0f)).toLong()
                state.basePositionMs + elapsed
            }
        }
        return clampPosition(raw, trackDurationMs)
    }

    fun evaluate(
        state: AuthoritativePlaybackState,
        trackDurationMs: Long,
        localPositionMs: Long,
        localIsPlaying: Boolean,
        serverNowMs: Long,
        localNowMs: Long,
        forceImmediateSeek: Boolean = false
    ): CorrectionAction {
        val targetPos = projectTargetPositionMs(state, serverNowMs, trackDurationMs)

        if (!state.isPlaying) {
            val posDiff = abs(localPositionMs - targetPos)
            return if (localIsPlaying || posDiff > 120L) {
                CorrectionAction.PauseAt(targetPos)
            } else {
                CorrectionAction.InSync
            }
        }

        // Future scheduled start: cue to basePositionMs and wait exact delta before play()
        if (state.scheduledStartServerTimeMs > serverNowMs + 25L) {
            val delay = (state.scheduledStartServerTimeMs - serverNowMs).coerceAtMost(3000L)
            return CorrectionAction.ScheduleStart(
                targetPositionMs = clampPosition(state.basePositionMs, trackDurationMs),
                delayMs = delay
            )
        }

        val driftMs = targetPos - localPositionMs // positive = local is behind; negative = local is ahead
        val absDrift = abs(driftMs)

        if (!localIsPlaying) {
            lastHardSeekLocalMs = localNowMs
            return CorrectionAction.HardSeek(targetPositionMs = targetPos, driftMs = driftMs)
        }

        if (absDrift <= deadbandMs) {
            return CorrectionAction.InSync
        }

        if (absDrift <= softCorrectionMaxDriftMs && !forceImmediateSeek) {
            val speed = if (driftMs > 0L) catchUpSpeed else slowDownSpeed
            return CorrectionAction.AdjustSpeed(speed = speed, driftMs = driftMs)
        }

        // Large drift: apply hard seek unless cooldown is active (in which case nudge speed temporarily)
        val cooldownElapsed = localNowMs - lastHardSeekLocalMs >= seekCooldownMs
        if (forceImmediateSeek || cooldownElapsed || absDrift > 2000L) {
            lastHardSeekLocalMs = localNowMs
            return CorrectionAction.HardSeek(targetPositionMs = targetPos, driftMs = driftMs)
        }

        val fallbackSpeed = if (driftMs > 0L) catchUpSpeed else slowDownSpeed
        return CorrectionAction.AdjustSpeed(speed = fallbackSpeed, driftMs = driftMs)
    }

    private fun clampPosition(positionMs: Long, trackDurationMs: Long): Long {
        val nonNegative = positionMs.coerceAtLeast(0L)
        return if (trackDurationMs > 0L) nonNegative.coerceAtMost(trackDurationMs) else nonNegative
    }
}
