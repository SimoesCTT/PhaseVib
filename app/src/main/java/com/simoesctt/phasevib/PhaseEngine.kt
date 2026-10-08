package com.simoesctt.phasevib

import org.jtransforms.fft.DoubleFFT_1D
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Phase-domain processing for multi-channel sensor data.
 * Same math as PhaseScope, applied to gyroscope/accelerometer channels.
 */
object PhaseEngine {

    data class Result(
        val kuramotoR: Double,
        val meanCoherence: Double,
        val cpuTimeNs: Long,
        val phaseTimeNs: Long
    )

    /**
     * Standard CPU pipeline: FFT of each channel, report magnitude peaks.
     */
    fun cpuPipeline(channels: Array<DoubleArray>, fs: Double): Pair<Double, Long> {
        val t0 = System.nanoTime()
        var totalMag = 0.0
        for (ch in channels) {
            val n = ch.size
            val data = DoubleArray(n * 2)
            for (i in 0 until n) data[2 * i] = ch[i]
            val fft = DoubleFFT_1D(n.toLong())
            fft.complexForward(data)
            var peak = 0.0
            for (i in 1 until n / 2) {
                val re = data[2 * i]; val im = data[2 * i + 1]
                val mag = sqrt(re * re + im * im)
                if (mag > peak) peak = mag
            }
            totalMag += peak
        }
        val elapsed = System.nanoTime() - t0
        return (totalMag / channels.size) to elapsed
    }

    /**
     * Phase pipeline: Hilbert transform → instantaneous phase → Kuramoto r
     * and mean pairwise coherence.
     */
    fun phasePipeline(channels: Array<DoubleArray>): Result {
        val t0 = System.nanoTime()

        val n = channels.size
        val length = channels[0].size

        // 1. Hilbert transform each channel → instantaneous phase
        val phases = Array(n) { hilbertPhase(channels[it]) }

        // 2. Kuramoto order parameter r = |mean over channels of e^{iφ}|
        var sumR = 0.0
        for (t in 0 until length) {
            var reT = 0.0
            var imT = 0.0
            for (i in 0 until n) {
                reT += cos(phases[i][t])
                imT += sin(phases[i][t])
            }
            sumR += sqrt(reT * reT + imT * imT) / n
        }
        val kuramoto = if (length > 0) sumR / length else 0.0

        // 3. Mean pairwise coherence
        var cohSum = 0.0
        var cohCount = 0
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                var re = 0.0
                var im = 0.0
                for (t in 0 until length) {
                    val d = phases[i][t] - phases[j][t]
                    re += cos(d); im += sin(d)
                }
                cohSum += sqrt(re * re + im * im) / length
                cohCount++
            }
        }
        val meanCoh = if (cohCount > 0) cohSum / cohCount else 0.0

        val elapsed = System.nanoTime() - t0
        return Result(kuramoto, meanCoh, 0L, elapsed)
    }

    private fun hilbertPhase(x: DoubleArray): DoubleArray {
        val n = x.size
        val data = DoubleArray(n * 2)
        for (i in 0 until n) data[2 * i] = x[i]
        val fft = DoubleFFT_1D(n.toLong())
        fft.complexForward(data)

        // Analytic signal: keep DC/Nyquist, double positive, zero negative
        for (k in 1 until n / 2) {
            data[2 * k] *= 2.0
            data[2 * k + 1] *= 2.0
        }
        for (k in n / 2 + 1 until n) {
            data[2 * k] = 0.0
            data[2 * k + 1] = 0.0
        }

        fft.complexInverse(data, false)

        val phase = DoubleArray(n)
        for (i in 0 until n) {
            phase[i] = atan2(data[2 * i + 1], data[2 * i])
        }
        return phase
    }
}
